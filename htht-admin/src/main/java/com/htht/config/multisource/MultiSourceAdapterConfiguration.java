package com.htht.config.multisource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.IDUtil;
import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.TaskArtifactStore;
import com.htht.multisource.application.port.out.ArtifactValidator;
import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.ExecutionIdGenerator;
import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.port.out.ProcessingBatchIdGenerator;
import com.htht.multisource.application.port.out.ProcessingBatchRepository;
import com.htht.multisource.application.port.out.ProcessingPlanner;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.ProductInspector;
import com.htht.multisource.application.port.out.StageIdGenerator;
import com.htht.multisource.application.port.out.TaskIdGenerator;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.AcceptWorkerEventUseCase;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.application.usecase.CreateProcessingBatchUseCase;
import com.htht.multisource.application.usecase.CreateProcessingTaskUseCase;
import com.htht.multisource.application.usecase.DispatchNextStageUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.PreflightProductUseCase;
import com.htht.multisource.application.usecase.RetryFailedStageUseCase;
import com.htht.multisource.application.usecase.service.AcceptWorkerEventService;
import com.htht.multisource.application.usecase.service.CancelProcessingTaskService;
import com.htht.multisource.application.usecase.service.CreateProcessingBatchService;
import com.htht.multisource.application.usecase.service.CreateProcessingTaskService;
import com.htht.multisource.application.usecase.service.GetTaskDetailService;
import com.htht.multisource.application.usecase.service.PreflightProductService;
import com.htht.multisource.application.usecase.service.DispatchNextStageService;
import com.htht.multisource.application.usecase.service.RetryFailedStageService;
import com.htht.multisource.application.usecase.service.TaskCreationCoordinator;
import com.htht.multisource.application.usecase.service.ArtifactRegistration;
import com.htht.multisource.application.usecase.service.ArtifactRegistrationService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.infrastructure.persistence.MyBatisArtifactRepository;
import com.htht.multisource.infrastructure.persistence.MyBatisExecutionEventRepository;
import com.htht.multisource.infrastructure.persistence.MyBatisProcessingBatchRepository;
import com.htht.multisource.infrastructure.persistence.MyBatisProcessingTaskRepository;
import com.htht.multisource.infrastructure.persistence.MyBatisTaskStageRepository;
import com.htht.multisource.infrastructure.persistence.id.LongSupplierProcessingBatchIdGenerator;
import com.htht.multisource.infrastructure.persistence.id.LongSupplierStageIdGenerator;
import com.htht.multisource.infrastructure.persistence.id.LongSupplierTaskIdGenerator;
import com.htht.multisource.infrastructure.persistence.mapper.ArtifactMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.BatchProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingBatchMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingTaskMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputAssetMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskEventMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskStageMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;
import com.htht.multisource.infrastructure.session.InMemoryPreflightSessionStore;
import com.htht.multisource.infrastructure.worker.LocalProcessWorkerGateway;
import com.htht.multisource.infrastructure.worker.LocalWorkerAssetManifestWriter;
import com.htht.multisource.infrastructure.worker.LocalWorkerArtifactValidator;
import com.htht.multisource.infrastructure.worker.LocalWorkerProcessLauncher;
import com.htht.multisource.infrastructure.worker.WorkerContractCodec;
import com.htht.multisource.infrastructure.worker.WorkerResultTranslator;
import com.htht.system.service.ISysFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(MultiSourceWorkerProperties.class)
public class MultiSourceAdapterConfiguration {
    private static final Duration EXECUTION_LEASE_DURATION = Duration.ofMinutes(5);
    private static final int MAX_STAGE_ATTEMPTS = 3;

    private final MultiSourceWorkerProperties properties;

    public MultiSourceAdapterConfiguration(MultiSourceWorkerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Supplier<AccessSubject> multisourceCurrentSubject() {
        return () -> {
            Long userId = LoginHelper.getUserId();
            if (userId == null || userId <= 0) {
                throw new SecurityException("Authenticated user is required.");
            }
            return new AccessSubject(userId);
        };
    }

    @Bean
    public SysFileAssetStorageAdapter multisourceAssetStorage(ISysFileService sysFileService,
                                                               @Value("${file.upload-path}") String uploadPath) {
        return new SysFileAssetStorageAdapter(sysFileService, resolveUploadRoot(uploadPath));
    }

    @Bean
    public TaskPersistenceMapper multisourceTaskPersistenceMapper() {
        return new TaskPersistenceMapper();
    }

    @Bean
    public ProcessingBatchRepository multisourceBatchRepository(ProcessingBatchMyBatisMapper batchMapper,
                                                                 BatchProductMyBatisMapper productMapper,
                                                                 TaskPersistenceMapper persistenceMapper) {
        return new MyBatisProcessingBatchRepository(batchMapper, productMapper, persistenceMapper);
    }

    @Bean
    public ProcessingTaskRepository multisourceTaskRepository(ProcessingTaskMyBatisMapper taskMapper,
                                                               TaskProductMyBatisMapper productMapper,
                                                               TaskInputAssetMyBatisMapper inputAssetMapper,
                                                               ProcessingBatchRepository batchRepository,
                                                               TaskPersistenceMapper persistenceMapper) {
        return new MyBatisProcessingTaskRepository(taskMapper, productMapper, inputAssetMapper, batchRepository, persistenceMapper);
    }

    @Bean
    public TaskStageRepository multisourceTaskStageRepository(TaskStageMyBatisMapper stageMapper,
                                                               TaskPersistenceMapper persistenceMapper) {
        return new MyBatisTaskStageRepository(stageMapper, persistenceMapper);
    }

    @Bean
    public ArtifactRepository multisourceArtifactRepository(ArtifactMyBatisMapper artifactMapper,
                                                             TaskPersistenceMapper persistenceMapper) {
        return new MyBatisArtifactRepository(artifactMapper, persistenceMapper);
    }

    @Bean
    public ExecutionEventRepository multisourceExecutionEventRepository(TaskEventMyBatisMapper eventMapper,
                                                                         TaskPersistenceMapper persistenceMapper) {
        return new MyBatisExecutionEventRepository(eventMapper, persistenceMapper);
    }

    @Bean
    public ExecutionClock multisourceExecutionClock() {
        return Instant::now;
    }

    @Bean
    public ExecutionIdGenerator multisourceExecutionIdGenerator() {
        return () -> new com.htht.multisource.domain.model.ExecutionId(UUID.randomUUID().toString());
    }

    @Bean
    public ArtifactValidator multisourceArtifactValidator(MultiSourceWorkerProperties properties) {
        return new LocalWorkerArtifactValidator(properties.toLocalProcessProperties().workRoot());
    }

    @Bean
    public ArtifactRegistration multisourceArtifactRegistration(ArtifactValidator validator,
                                                                ArtifactRepository artifactRepository,
                                                                TaskArtifactStore artifactStore) {
        return new ArtifactRegistrationService(validator, artifactRepository, artifactStore);
    }

    @Bean
    public TaskArtifactStore taskArtifactStore(MultiSourceWorkerProperties properties) {
        return new LocalTaskArtifactStore(properties.toLocalProcessProperties().workRoot(),
            properties.requireArtifactRoot());
    }

    @Bean
    public ProcessingBatchIdGenerator multisourceBatchIdGenerator() {
        return new LongSupplierProcessingBatchIdGenerator(IDUtil::getLongID);
    }

    @Bean
    public TaskIdGenerator multisourceTaskIdGenerator() {
        return new LongSupplierTaskIdGenerator(IDUtil::getLongID);
    }

    @Bean
    public StageIdGenerator multisourceStageIdGenerator() {
        return new LongSupplierStageIdGenerator(IDUtil::getLongID);
    }

    @Bean
    public LocalProcessWorkerGateway multisourceWorkerGateway(MultiSourceWorkerProperties properties,
                                                               SysFileAssetStorageAdapter assetStorage,
                                                               @Value("${file.upload-path}") String uploadPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        var workerProperties = properties.toLocalProcessProperties();
        Path uploadRoot = resolveUploadRoot(uploadPath);
        var manifestWriter = new LocalWorkerAssetManifestWriter(workerProperties.workRoot(), uploadRoot, assetStorage, Clock.systemUTC());
        return new LocalProcessWorkerGateway(
            new WorkerContractCodec(objectMapper), new WorkerResultTranslator(objectMapper),
            new LocalWorkerProcessLauncher(workerProperties), () -> UUID.randomUUID().toString(), manifestWriter
        );
    }

    @Bean
    public PreflightSessionStore multisourcePreflightSessionStore() {
        return new InMemoryPreflightSessionStore(Clock.systemUTC(), Duration.ofMinutes(30), () -> UUID.randomUUID().toString());
    }

    @Bean
    public PreflightProductUseCase preflightProductUseCase(LocalProcessWorkerGateway workerGateway,
                                                           SysFileAssetStorageAdapter assetStorage) {
        return new PreflightProductService((ProductInspector) workerGateway, assetStorage);
    }

    @Bean
    public TaskCreationCoordinator multisourceTaskCreationCoordinator(ProcessingTaskRepository taskRepository,
                                                                       TaskStageRepository stageRepository,
                                                                       LocalProcessWorkerGateway workerGateway,
                                                                       SysFileAssetStorageAdapter assetStorage,
                                                                       TaskIdGenerator taskIdGenerator,
                                                                       StageIdGenerator stageIdGenerator) {
        return new TaskCreationCoordinator(taskRepository, stageRepository, (ProcessingPlanner) workerGateway,
            assetStorage, taskIdGenerator, stageIdGenerator, properties.requireProcessorVersion());
    }

    @Bean
    public CreateProcessingTaskUseCase createProcessingTaskUseCase(ProcessingBatchRepository batchRepository,
                                                                    ProcessingBatchIdGenerator batchIdGenerator,
                                                                    TaskCreationCoordinator coordinator) {
        return new CreateProcessingTaskService(batchRepository, batchIdGenerator, coordinator);
    }

    @Bean
    public CreateProcessingBatchUseCase createProcessingBatchUseCase(ProcessingBatchRepository batchRepository,
                                                                      ProcessingBatchIdGenerator batchIdGenerator,
                                                                      TaskCreationCoordinator coordinator) {
        return new CreateProcessingBatchService(batchRepository, batchIdGenerator, coordinator);
    }

    @Bean
    public GetTaskDetailUseCase getTaskDetailUseCase(ProcessingTaskRepository taskRepository,
                                                     TaskStageRepository stageRepository,
                                                     ArtifactRepository artifactRepository) {
        return new GetTaskDetailService(taskRepository, stageRepository, artifactRepository);
    }

    @Bean
    public AcceptWorkerEventUseCase acceptWorkerEventUseCase(TaskStageRepository stageRepository,
                                                             ExecutionEventRepository eventRepository,
                                                             ArtifactRegistration artifactRegistration,
                                                             ProcessingTaskRepository taskRepository) {
        return new AcceptWorkerEventService(stageRepository, eventRepository, artifactRegistration, taskRepository);
    }

    @Bean
    public DispatchNextStageUseCase dispatchNextStageUseCase(ProcessingTaskRepository taskRepository,
                                                             TaskStageRepository stageRepository,
                                                             LocalProcessWorkerGateway workerGateway,
                                                             SysFileAssetStorageAdapter assetStorage,
                                                             ExecutionIdGenerator executionIdGenerator,
                                                             ExecutionClock executionClock,
                                                             AcceptWorkerEventUseCase eventUseCase) {
        return new DispatchNextStageService(taskRepository, stageRepository, workerGateway, assetStorage,
            executionIdGenerator, executionClock, EXECUTION_LEASE_DURATION, eventUseCase);
    }

    @Bean
    public CancelProcessingTaskUseCase cancelProcessingTaskUseCase(ProcessingTaskRepository taskRepository,
                                                                    TaskStageRepository stageRepository,
                                                                    LocalProcessWorkerGateway workerGateway,
                                                                    ExecutionClock executionClock) {
        return new CancelProcessingTaskService(taskRepository, stageRepository, workerGateway, executionClock);
    }

    @Bean
    public RetryFailedStageUseCase retryFailedStageUseCase(ProcessingTaskRepository taskRepository,
                                                           TaskStageRepository stageRepository) {
        return new RetryFailedStageService(taskRepository, stageRepository, MAX_STAGE_ATTEMPTS);
    }

    @Bean
    public ServerOutputUriAllocator multisourceOutputUriAllocator() {
        return new ServerOutputUriAllocator(() -> UUID.randomUUID().toString());
    }

    /**
     * Resolve the configured upload directory explicitly instead of relying on
     * Spring's String-to-Path converter, which treats Windows drive paths as
     * relative paths in a servlet web application.
     */
    static Path resolveUploadRoot(String uploadPath) {
        if (uploadPath == null || uploadPath.isBlank()) {
            throw new IllegalStateException("file.upload-path must be configured.");
        }
        return Path.of(uploadPath.trim()).toAbsolutePath().normalize();
    }
}
