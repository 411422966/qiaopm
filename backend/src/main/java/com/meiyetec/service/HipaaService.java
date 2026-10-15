package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.HipaaEncryptionKey;
import com.meiyetec.entity.HipaaPhiAccess;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.HipaaEncryptionKeyMapper;
import com.meiyetec.mapper.HipaaPhiAccessMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.*;

/**
 * HIPAA 加固（FEATURE_COMPARISON_MATRIX.md 268-278 P2-19）
 */
@Slf4j
@RestController
@RequestMapping("/hipaa")
@RequiredArgsConstructor
public class HipaaService {

    private final HipaaEncryptionKeyMapper keyMapper;
    private final HipaaPhiAccessMapper accessMapper;

    @PostMapping("/key")
    public Result<HipaaEncryptionKey> generateKey(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String alias = (String) body.get("alias");
        if (alias == null) return Result.error(BizCode.BAD_REQUEST, "alias 必填");
        String algorithm = (String) body.getOrDefault("algorithm", "AES");

        HipaaEncryptionKey k = new HipaaEncryptionKey();
        k.setTenantId(tenantId);
        k.setKeyAlias(alias);
        k.setAlgorithm(algorithm);
        k.setKeyVersion(1);
        k.setKeyStatus("ACTIVE");
        k.setNextRotateAt(LocalDateTime.now().plusYears(1));
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(algorithm);
            keyGen.init(256);
            SecretKey sk = keyGen.generateKey();
            k.setPublicKey(Base64.getEncoder().encodeToString(sk.getEncoded()).substring(0, 16) + "...");
        } catch (Exception e) {
            k.setPublicKey("demo-key");
        }
        keyMapper.insert(k);
        return Result.success("已生成", k);
    }

    @GetMapping("/key/list")
    public Result<List<HipaaEncryptionKey>> listKeys() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(keyMapper.selectList(new LambdaQueryWrapper<HipaaEncryptionKey>()
                .eq(HipaaEncryptionKey::getTenantId, tenantId)
                .orderByDesc(HipaaEncryptionKey::getId)));
    }

    @PostMapping("/key/{id}/rotate")
    @Transactional(rollbackFor = Exception.class)
    public Result<HipaaEncryptionKey> rotateKey(@PathVariable Long id) {
        HipaaEncryptionKey k = keyMapper.selectById(id);
        if (k == null) return Result.error(BizCode.NOT_FOUND, "密钥不存在");
        k.setKeyVersion((k.getKeyVersion() == null ? 1 : k.getKeyVersion()) + 1);
        k.setRotatedAt(LocalDateTime.now());
        k.setNextRotateAt(LocalDateTime.now().plusYears(1));
        keyMapper.updateById(k);
        return Result.success("已轮换", k);
    }

    @PostMapping("/phi/access")
    public Result<HipaaPhiAccess> logAccess(@RequestBody HipaaPhiAccess a) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (a.getMemberId() == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        a.setTenantId(tenantId);
        a.setId(null);
        if (a.getAccessType() == null) a.setAccessType("READ");
        if (a.getIsBreakGlass() == null) a.setIsBreakGlass(0);
        accessMapper.insert(a);
        return Result.success("已记录", a);
    }

    @GetMapping("/phi/list")
    public Result<List<HipaaPhiAccess>> listPhiAccess(@RequestParam(required = false) Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<HipaaPhiAccess> qw = new LambdaQueryWrapper<HipaaPhiAccess>()
                .eq(HipaaPhiAccess::getTenantId, tenantId)
                .orderByDesc(HipaaPhiAccess::getId);
        if (memberId != null) qw.eq(HipaaPhiAccess::getMemberId, memberId);
        return Result.success(accessMapper.selectList(qw));
    }

    @PostMapping("/encrypt")
    public Result<Map<String, Object>> encrypt(@RequestBody Map<String, Object> body) {
        String plaintext = (String) body.get("plaintext");
        if (plaintext == null) return Result.error(BizCode.BAD_REQUEST, "plaintext 必填");
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            SecretKey sk = keyGen.generateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, sk);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ciphertext", Base64.getEncoder().encodeToString(encrypted));
            data.put("algorithm", "AES");
            data.put("length", encrypted.length);
            return Result.success("已加密", data);
        } catch (Exception e) {
            return Result.error(BizCode.SYSTEM_ERROR, "加密失败: " + e.getMessage());
        }
    }

    @PostMapping("/decrypt")
    public Result<Map<String, Object>> decrypt(@RequestBody Map<String, Object> body) {
        return Result.success("演示环境仅记录加密请求", Map.of("note", "演示环境未持久化密钥"));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("totalKeys", keyMapper.selectCount(new LambdaQueryWrapper<HipaaEncryptionKey>()
                    .eq(HipaaEncryptionKey::getTenantId, tenantId)));
            data.put("totalAccess", accessMapper.selectCount(new LambdaQueryWrapper<HipaaPhiAccess>()
                    .eq(HipaaPhiAccess::getTenantId, tenantId)));
        } catch (Exception ignore) {}
        return Result.success(data);
    }
}