package com.htht.common.utils;

import com.antherd.smcrypto.sm2.Keypair;
import com.antherd.smcrypto.sm2.Sm2;
import com.antherd.smcrypto.sm3.Sm3;
import com.antherd.smcrypto.sm4.Sm4;
import com.antherd.smcrypto.sm4.Sm4Options;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * @author liaozhenshan
 * @date 2024/11/21 15:17
 * @version 1.0
 */
@Slf4j
public class SmUtils {

    /**
     * 获取sm2公钥和私钥
     */
    public static Map<String, String> generateSm2Key() {
        Map<String, String> keyMap = new HashMap<>(2);
        Keypair keypair = Sm2.generateKeyPairHex();
        String privateKey = keypair.getPrivateKey(); // 公钥
        String publicKey = keypair.getPublicKey(); // 私钥
        keyMap.put("privateKey", privateKey);
        keyMap.put("publicKey", publicKey);
        return keyMap;
    }

    /**
     * 加密方法（Sm2 的专门针对前后端分离，非对称秘钥对的方式，暴露出去的公钥，对传输过程中的密码加个密）
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param str 待加密数据
     * @return 加密后的密文
     */
    public static String doSm2Encrypt(String str,String publicKey) {
        return Sm2.doEncrypt(str, publicKey);
    }

    /**
     * 解密方法
     * 如果采用加密机的方法，用try catch 捕捉异常，返回原文值即可
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param str 密文
     * @return 解密后的明文
     */
    public static String doSm2Decrypt(String str,String privateKey) {
        // 解密
        return Sm2.doDecrypt(str, privateKey);
    }

    /**
     * 加密方法
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param str 待加密数据
     * @return 加密后的密文
     */
    public static String doSm4CbcEncrypt(String str,String key) {
        // SM4 加密  cbc模式
        Sm4Options sm4Options4 = new Sm4Options();
        sm4Options4.setMode("cbc");
        sm4Options4.setIv("fedcba98765432100123456789abcdef");
        return Sm4.encrypt(str, key, sm4Options4);
    }

    /**
     * 解密方法
     * 如果采用加密机的方法，用try catch 捕捉异常，返回原文值即可
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param str 密文
     * @return 解密后的明文
     */
    public static String doSm4CbcDecrypt(String str,String key) {
        // 解密，cbc 模式，输出 utf8 字符串
        Sm4Options sm4Options8 = new Sm4Options();
        sm4Options8.setMode("cbc");
        sm4Options8.setIv("fedcba98765432100123456789abcdef");
        String docString =  Sm4.decrypt(str, key, sm4Options8);
        if ("".equals(docString)) {
            log.warn(">>> 字段解密失败，返回原文值：{}", str);
            return str;
        } else {
            return docString;
        }
    }

    /**
     * 纯签名
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param str 待签名数据
     * @return 签名结果
     */
    public static String doSignature(String str,String privateKey) {
        return Sm2.doSignature(str, privateKey);
    }

    /**
     * 验证签名结果
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param originalStr 签名原文数据
     * @param str 签名结果
     * @return 是否通过
     */
    public static boolean doVerifySignature(String originalStr, String str, String publicKey) {
        return Sm2.doVerifySignature(originalStr, str, publicKey);
    }

    /**
     * 通过杂凑算法取得hash值，用于做数据完整性保护
     *
     * @author yubaoshan
     * @date 2022/9/15 21:51
     * @param str 字符串
     * @return hash 值
     */
    public static String doHashValue(String str) {
        return Sm3.sm3(str);
    }
}
