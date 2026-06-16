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
                                          @RequestParam(required = false) String timestamp,
                                          @RequestParam(required = false) String nonce,
                                          @RequestBody String body) {
        if (!wechat.verify(signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(wechat.handle(body));
    }
}
