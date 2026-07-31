package com.htht.test;

import com.antherd.smcrypto.sm2.Sm2;
import com.htht.common.utils.SmUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

/**
 * @author liaozhenshan
 * @version 1.0
 * @date 2024/11/21 13:40
 */
@SpringBootTest
@DisplayName("sm单元测试")
public class SmUnitTest {

    @DisplayName("生产密钥对")
    @Test
    public void genKeyPair() {
        Map<String, String> keyPair = SmUtils.generateSm2Key();
        System.out.println("公钥：" + keyPair.get("publicKey"));
        System.out.println("私钥：" + keyPair.get("privateKey"));
    }

    @DisplayName("加密解密")
    @Test
    public void encryptAndDecrypt() {
        String data = "Hello World";
        String publicKey = "049b15a76f28fecfcc2cf80c213982277e55ba6687d046b34570aec1afba5d3b77522458005f121083287cceb6137362e4b4dcfbc20c01944fdae81398dbc46a35";
        String privateKey = "008e3dff7a7ff5d52dd698a3960007a3557e01b69cca9d34e2ff51fea8e1b5e8d0";
        // cipherMode 1 - C1C3C2，0 - C1C2C3，默认为1
        String encryptData = Sm2.doEncrypt(data, publicKey); // 加密结果
        String decryptData = Sm2.doDecrypt(encryptData, privateKey); // 解密结果
        System.out.println("加密结果：" + encryptData);
        System.out.println("解密结果：" + decryptData);
    }
}
