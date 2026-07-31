package com.htht.algorithm.data.client;

import com.bj58.ailab.dlpredictonline.entity.PredictionProtos;
import com.bj58.ailab.dlpredictonline.grpc.WpaiDLPredictOnlineServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.Value;
import com.htht.common.exception.GlobalException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.util.Strings;
import tensorflow.serving.Predict;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 算法调用客户端
 *
 * @author wjx
 * @since 2023-6-9
 */
//@Component
public class AlgorithmClient {

    private final static Map<String, AlgorithmClient> cacheMap = new ConcurrentHashMap<>();

//    private AlgorithmProperties algorithmProperties;

    private ManagedChannel channel;

    /**
     * 阻塞/同步的stub（存根）
     */
    private WpaiDLPredictOnlineServiceGrpc.WpaiDLPredictOnlineServiceBlockingStub blockingStub = null;

    /**
     * 非阻塞/异步的stub
     */
    private WpaiDLPredictOnlineServiceGrpc.WpaiDLPredictOnlineServiceStub async = null;

    public AlgorithmClient(String ip, Integer port) {
        this.channel = ManagedChannelBuilder.forAddress(ip, port)
            .usePlaintext()
            .maxInboundMessageSize(10485760)
            .build();
        this.blockingStub = WpaiDLPredictOnlineServiceGrpc.newBlockingStub(this.channel);
        this.async = WpaiDLPredictOnlineServiceGrpc.newStub(this.channel);
    }

    public void shutdown() throws InterruptedException {
        if (Objects.nonNull(this.channel)) {
            this.channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 算法调用
     *
     * @param content
     * @param taskId
     * @return
     */
    public List<Double> greet(byte[] content, int taskId) {
        PredictionProtos.SeldonMessage request = buildRequest(content, taskId);
        PredictionProtos.SeldonMessage response = blockingStub.withDeadlineAfter(10000000, TimeUnit.MILLISECONDS).pytorchPredict(request);
        return response.getData().getTensor().getValuesList();
    }

    /**
     * 算法调用
     *
     * @param path
     * @param taskId
     * @return
     * @throws IOException
     */
    public List<Double> greet(String path, int taskId) throws IOException {
        byte[] content = getBytesFromFile(path);
        return greet(content, taskId);
    }

    /**
     * 构建请求
     *
     * @param content 文件byte数组
     * @param id      算法id
     * @return
     */
    private PredictionProtos.SeldonMessage buildRequest(byte[] content, int id) {
        ByteString byteString = ByteString.copyFrom(content);
        Map<String, Value> tagsMap = new HashMap<>(1);
        Value taskId = Value.newBuilder().setNumberValue(id).build();
        tagsMap.put("taskid", taskId);
        PredictionProtos.Meta meta = PredictionProtos.Meta.newBuilder().putAllTags(tagsMap).build();
        PredictionProtos.SeldonMessage request = PredictionProtos.SeldonMessage.newBuilder().setMeta(meta)
            .setBinData(byteString).build();
        return request;
    }

    /**
     * 读取文件转byte数组
     *
     * @param path
     * @return
     * @throws IOException
     */
    private byte[] getBytesFromFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new GlobalException("切片文件不存在:" + path);
        }
        FileInputStream in = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] temp = new byte[1024];
        int length = -1;
        while ((length = in.read(temp)) != -1) {
            out.write(temp, 0, length);
        }
        byte[] content = out.toByteArray();
        return content;
    }


    public static AlgorithmClient getClient(String ip, Integer port) {
        if (Strings.isNotBlank(ip) && Objects.nonNull(port)) {
            String key = ip + "---" + port;
            AlgorithmClient client = cacheMap.get(key);
            if (Objects.isNull(client)) {
                client = new AlgorithmClient(ip, port);
                cacheMap.put(key, client);
            }
            return client;
        }
        return null;
    }

}
