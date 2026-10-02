package com.htht.config.multisource;

import com.htht.multisource.application.port.out.ManagedInputFilePurgePort;
import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Filesystem boundary for source cleanup. It never deletes an unregistered path. */
public final class LocalManagedInputFilePurgeService implements ManagedInputFilePurgePort {
    private final Path uploadRoot;
    private final ManagedInputFileRepository managedFiles;
    private final TaskInputReferenceRepository references;

    public LocalManagedInputFilePurgeService(Path uploadRoot, ManagedInputFileRepository managedFiles,
                                             TaskInputReferenceRepository references) {
        this.uploadRoot = Objects.requireNonNull(uploadRoot, "uploadRoot").toAbsolutePath().normalize();
        this.managedFiles = Objects.requireNonNull(managedFiles, "managedFiles");
        this.references = Objects.requireNonNull(references, "references");
    }

    @Override public Result purge(ManagedInputFileRepository.ManagedInputFile managedFile) {
        Objects.requireNonNull(managedFile, "managedFile");
        Path candidate = managedFile.path().toAbsolutePath().normalize();
        if (!candidate.startsWith(uploadRoot) || candidate.equals(uploadRoot))
            throw new IllegalArgumentException("Managed input path escapes the configured upload root.");
        if (!references.findActiveReferences(managedFile.id()).isEmpty()) return Result.PROTECTED;
        try {
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                managedFiles.markPurged(managedFile.id(), managedFile.version());
                return Result.MISSING;
            }
            Path realRoot = uploadRoot.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot) || Files.isSymbolicLink(candidate))
                throw new IllegalArgumentException("Managed input path escapes the configured upload root.");
            // Recheck after all filesystem validation and immediately before deletion.
            if (!references.findActiveReferences(managedFile.id()).isEmpty()) return Result.PROTECTED;
            Files.delete(realCandidate);
            managedFiles.markPurged(managedFile.id(), managedFile.version());
            return Result.PURGED;
        } catch (IOException exception) {
            try { managedFiles.markPurgeFailed(managedFile.id(), managedFile.version(), exception.getMessage()); }
            catch (RuntimeException ignored) { }
            throw new IllegalStateException("Managed input file deletion failed.", exception);
        }
    }
}
