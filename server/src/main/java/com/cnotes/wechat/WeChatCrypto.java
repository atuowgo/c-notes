package com.cnotes.wechat;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 微信公众号<b>安全模式</b>消息加解密(官方 WXBizMsgCrypt 算法的精简实现)。
 *
 * <p>密钥:EncodingAESKey 为 43 位 base64(无 padding),{@code AESKey = base64decode(key + "=")} 得 32 字节;
 * IV 取 AESKey 前 16 字节。算法 AES-256-CBC,<b>块大小 32 的 PKCS#7</b> 补位。
 *
 * <p>明文报文结构:{@code 16 字节随机数 + 4 字节消息长度(网络字节序) + 消息体 + AppID}。
 * 解密后校验 AppID,防止把别家公众号的消息当成自己的。
 */
public class WeChatCrypto {

    private static final int BLOCK_SIZE = 32;

    private final byte[] aesKey;   // 32 字节
    private final byte[] iv;       // AESKey 前 16 字节
    private final String appId;
    private final SecureRandom random = new SecureRandom();

    public WeChatCrypto(String encodingAesKey, String appId) {
        if (encodingAesKey == null || encodingAesKey.length() != 43) {
            throw new IllegalArgumentException("EncodingAESKey 必须是 43 位");
        }
        this.aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
        if (aesKey.length != 32) {
            throw new IllegalArgumentException("EncodingAESKey 解码后应为 32 字节");
        }
        this.iv = Arrays.copyOf(aesKey, 16);
        this.appId = appId == null ? "" : appId;
    }

    /** 解出明文消息体;AppID 不匹配抛异常。 */
    public String decrypt(String encryptBase64) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(encryptBase64);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            byte[] plain = pkcs7Unpad(cipher.doFinal(cipherBytes));

            int msgLen = ((plain[16] & 0xff) << 24) | ((plain[17] & 0xff) << 16)
                       | ((plain[18] & 0xff) << 8) | (plain[19] & 0xff);
            String msg = new String(plain, 20, msgLen, StandardCharsets.UTF_8);
            String fromAppId = new String(plain, 20 + msgLen,
                plain.length - 20 - msgLen, StandardCharsets.UTF_8);
            if (!appId.isBlank() && !appId.equals(fromAppId)) {
                throw new IllegalStateException("AppID 不匹配");
            }
            return msg;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("微信消息解密失败", e);
        }
    }

    /** 把明文回复加密为 base64 密文(供放进 <Encrypt> 节点)。 */
    public String encrypt(String plaintext) {
        try {
            byte[] msg = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] rand = new byte[16];
            random.nextBytes(rand);
            byte[] networkLen = {
                (byte) (msg.length >>> 24), (byte) (msg.length >>> 16),
                (byte) (msg.length >>> 8), (byte) msg.length
            };
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(rand);
            bos.write(networkLen);
            bos.write(msg);
            bos.write(appId.getBytes(StandardCharsets.UTF_8));
            byte[] padded = pkcs7Pad(bos.toByteArray());

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
        } catch (Exception e) {
            throw new IllegalStateException("微信消息加密失败", e);
        }
    }

    /** PKCS#7 补位到 32 的倍数(微信用块大小 32)。 */
    private static byte[] pkcs7Pad(byte[] data) {
        int pad = BLOCK_SIZE - (data.length % BLOCK_SIZE);
        if (pad == 0) pad = BLOCK_SIZE;
        byte[] out = Arrays.copyOf(data, data.length + pad);
        Arrays.fill(out, data.length, out.length, (byte) pad);
        return out;
    }

    private static byte[] pkcs7Unpad(byte[] data) {
        int pad = data[data.length - 1];
        if (pad < 1 || pad > BLOCK_SIZE) pad = 0;
        return Arrays.copyOf(data, data.length - pad);
    }
}
