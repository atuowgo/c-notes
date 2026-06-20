package com.cnotes.wechat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 微信公众号回调端点(直连本服务器,无云函数中转)。
 * - GET:服务器配置校验,签名通过则回显 echostr。
 * - POST:接收消息,入库后立即被动回复(5 秒内)。
 */
@RestController
@RequestMapping("/wechat/callback")
@RequiredArgsConstructor
public class WeChatController {

    private final WeChatService wechat;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam String signature,
                                         @RequestParam String timestamp,
                                         @RequestParam String nonce,
                                         @RequestParam String echostr) {
        return wechat.verify(signature, timestamp, nonce)
            ? ResponseEntity.ok(echostr)
            : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(produces = MediaType.TEXT_XML_VALUE)
    public ResponseEntity<String> receive(@RequestParam(required = false) String signature,
                                          @RequestParam(name = "msg_signature", required = false) String msgSignature,
                                          @RequestParam(required = false) String timestamp,
                                          @RequestParam(required = false) String nonce,
                                          @RequestParam(name = "encrypt_type", required = false) String encryptType,
                                          @RequestBody String body) {
        // 安全模式:encrypt_type=aes(或服务端已配 aes-key 且报文带 <Encrypt>)→ 走加解密路径,
        // 验签用 msg_signature(含密文)。handleEncrypted 返回 null 表示验签/解密失败。
        boolean secure = "aes".equalsIgnoreCase(encryptType)
            || (wechat.secureModeEnabled() && body != null && body.contains("<Encrypt>"));
        if (secure) {
            String out = wechat.handleEncrypted(body, msgSignature, timestamp, nonce);
            return out == null
                ? ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                : ResponseEntity.ok(out);
        }

        if (!wechat.verify(signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(wechat.handle(body));
    }
}
