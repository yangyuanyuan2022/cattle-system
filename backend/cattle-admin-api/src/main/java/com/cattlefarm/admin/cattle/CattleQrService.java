package com.cattlefarm.admin.cattle;

import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.cattle.service.CattleService;
import com.cattlefarm.admin.common.DataConflictException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class CattleQrService {
    private final AuthService auth;
    private final CattleService cattle;
    private final byte[] secret;
    private final String publicBaseUrl;

    public CattleQrService(AuthService auth, CattleService cattle,
                           @Value("${cattle.qr.secret:${QR_TOKEN_SECRET:local-cattle-qr-secret-change-in-production}}") String secret,
                           @Value("${app.public-base-url:http://127.0.0.1:8080}") String publicBaseUrl) {
        this.auth = auth;
        this.cattle = cattle;
        if (secret.length() < 32) throw new IllegalStateException("二维码签名密钥长度不能少于 32 位");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public byte[] qrcode(long cattleId) {
        CattleResponse item = cattle.detail(cattleId);
        long farm = auth.currentFarmId();
        String token = token(farm, Long.parseLong(item.cattleId()));
        String content = publicBaseUrl + "/api/v1/cattle/scan?token=" + token;
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("二维码生成失败", exception);
        }
    }

    public CattleResponse scan(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String value = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = value.split(":", 3);
            if (parts.length != 3) throw new IllegalArgumentException();
            long farm = Long.parseLong(parts[0]), cattleId = Long.parseLong(parts[1]);
            byte[] expected = sign(parts[0] + ":" + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual) || farm != auth.currentFarmId()) throw new DataConflictException("二维码无效或不属于当前牛场");
            return cattle.detail(cattleId);
        } catch (DataConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DataConflictException("二维码令牌格式错误");
        }
    }

    private String token(long farm, long cattleId) {
        String payload = farm + ":" + cattleId;
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("二维码签名失败", exception);
        }
    }
}
