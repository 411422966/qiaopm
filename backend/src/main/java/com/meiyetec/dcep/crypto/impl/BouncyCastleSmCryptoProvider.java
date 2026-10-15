package com.meiyetec.dcep.crypto.impl;

import com.meiyetec.dcep.crypto.Sm2KeyPair;
import com.meiyetec.dcep.crypto.Sm2Signature;
import com.meiyetec.dcep.crypto.SmCryptoProvider;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;

/**
 * DCEP-03：基于 BouncyCastle 的 SM 国密算法实现
 *
 * <p>SM2/SM3/SM4 完整算法栈（软件实现，无需硬件）。生产环境可替换为 HSM 实现。
 *
 * <p>使用的曲线：sm2p256v1（GM/T 0003-2012 标准）
 *
 * @see <a href="http://www.gmbz.org.cn/main/bzl.html">GM/T 0003-2012 SM2 椭圆曲线公钥密码算法</a>
 */
@Slf4j
public class BouncyCastleSmCryptoProvider implements SmCryptoProvider {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final X9ECParameters SM2_CURVE_PARAMS = GMNamedCurves.getByName("sm2p256v1");
    private static final ECDomainParameters SM2_DOMAIN = new ECDomainParameters(
            SM2_CURVE_PARAMS.getCurve(),
            SM2_CURVE_PARAMS.getG(),
            SM2_CURVE_PARAMS.getN(),
            SM2_CURVE_PARAMS.getH());

    /** 标准 userId（"1234567812345678"）— 与 SM2 标准签名算法 ZA 计算一致 */
    private static final byte[] DEFAULT_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random = new SecureRandom();

    @Override
    public Sm2KeyPair generateSm2KeyPair() {
        ECKeyPairGenerator gen = new ECKeyPairGenerator();
        gen.init(new ECKeyGenerationParameters(SM2_DOMAIN, random));
        org.bouncycastle.crypto.AsymmetricCipherKeyPair pair = gen.generateKeyPair();

        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) pair.getPrivate();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        byte[] pubUncompressed = pub.getQ().getEncoded(false);
        return new Sm2KeyPair(priv.getD(), pubUncompressed);
    }

    @Override
    public Sm2Signature sm2Sign(BigInteger privateKey, byte[] publicKey, byte[] plaintext) {
        try {
            ECPrivateKeyParameters privParams = new ECPrivateKeyParameters(privateKey, SM2_DOMAIN);
            // SM2 签名器构造需要公钥（用于 ZA 计算），所以这里强制要求传公钥
            ECPublicKeyParameters pubParams = publicKeyFromUncompressed(publicKey);

            SM2Signer signer = new SM2Signer();
            CipherParameters cp = new ParametersWithRandom(privParams, random);
            signer.init(true, cp);
            signer.update(plaintext, 0, plaintext.length);
            byte[] sigDer = signer.generateSignature();
            return parseDerSignature(sigDer);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SM2 sign failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sm2Verify(byte[] publicKey, byte[] plaintext, Sm2Signature signature) {
        try {
            ECPublicKeyParameters pubParams = publicKeyFromUncompressed(publicKey);

            SM2Signer signer = new SM2Signer();
            signer.init(false, pubParams);
            signer.update(plaintext, 0, plaintext.length);
            byte[] sigDer = encodeDerSignature(signature.getR(), signature.getS());
            return signer.verifySignature(sigDer);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SM2 verify failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] sm2Encrypt(byte[] publicKey, byte[] plaintext) {
        try {
            ECPublicKeyParameters pubParams = publicKeyFromUncompressed(publicKey);
            SM2Engine engine = new SM2Engine();
            engine.init(true, new ParametersWithRandom(pubParams, random));
            return engine.processBlock(plaintext, 0, plaintext.length);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SM2 encrypt failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] sm2Decrypt(BigInteger privateKey, byte[] ciphertext) {
        try {
            ECPrivateKeyParameters privParams = new ECPrivateKeyParameters(privateKey, SM2_DOMAIN);
            SM2Engine engine = new SM2Engine();
            engine.init(false, privParams);
            return engine.processBlock(ciphertext, 0, ciphertext.length);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SM2 decrypt failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] sm3Hash(byte[] input) {
        SM3Digest digest = new SM3Digest();
        digest.update(input, 0, input.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    @Override
    public String sm3HashHex(String input) {
        byte[] hash = sm3Hash(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02X", b & 0xff));
        }
        return sb.toString();
    }

    @Override
    public byte[] sm4Encrypt(byte[] key, byte[] iv, byte[] plaintext) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 key 必须 16 字节，实际=" + (key == null ? "null" : key.length));
        }
        if (iv == null || iv.length != 16) {
            throw new IllegalArgumentException("SM4 IV 必须 16 字节，实际=" + (iv == null ? "null" : iv.length));
        }
        try {
            SM4Engine engine = new SM4Engine();
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(engine));
            // CBC 模式下 IV 通过 ParametersWithIV 传递
            org.bouncycastle.crypto.params.ParametersWithIV paramsWithIV =
                    new org.bouncycastle.crypto.params.ParametersWithIV(
                            new org.bouncycastle.crypto.params.KeyParameter(key), iv);
            cipher.init(true, paramsWithIV);
            return cipherDoFinal(cipher, plaintext);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SM4 encrypt failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] sm4Decrypt(byte[] key, byte[] iv, byte[] ciphertext) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 key 必须 16 字节，实际=" + (key == null ? "null" : key.length));
        }
        if (iv == null || iv.length != 16) {
            throw new IllegalArgumentException("SM4 IV 必须 16 字节，实际=" + (iv == null ? "null" : iv.length));
        }
        try {
            SM4Engine engine = new SM4Engine();
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(engine));
            org.bouncycastle.crypto.params.ParametersWithIV paramsWithIV =
                    new org.bouncycastle.crypto.params.ParametersWithIV(
                            new org.bouncycastle.crypto.params.KeyParameter(key), iv);
            cipher.init(false, paramsWithIV);
            return cipherDoFinal(cipher, ciphertext);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SM4 decrypt failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "bouncycastle-" + BouncyCastleProvider.PROVIDER_NAME;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private static byte[] cipherDoFinal(PaddedBufferedBlockCipher cipher, byte[] input) {
        try {
            byte[] outBuf = new byte[cipher.getOutputSize(input.length)];
            int len = cipher.processBytes(input, 0, input.length, outBuf, 0);
            len += cipher.doFinal(outBuf, len);
            byte[] out = new byte[len];
            System.arraycopy(outBuf, 0, out, 0, len);
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Cipher doFinal failed: " + e.getMessage(), e);
        }
    }

    private static ECPublicKeyParameters publicKeyFromUncompressed(byte[] pubUncompressed) {
        if (pubUncompressed == null) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        // BouncyCastle 的 ECPoint.getEncoded(false) 实际返回 65 字节（04 || X || Y），
        // 但旧版本 / 部分曲线可能只返回 64 字节（X || Y）。两种格式都兼容：
        if (pubUncompressed.length == 65 && pubUncompressed[0] == 0x04) {
            // 标准格式：04 || X || Y
            ECPoint point = SM2_DOMAIN.getCurve().decodePoint(pubUncompressed);
            return new ECPublicKeyParameters(point, SM2_DOMAIN);
        } else if (pubUncompressed.length == 64) {
            // 紧凑格式：X || Y（无 04 前缀），自动补 04
            byte[] withPrefix = new byte[65];
            withPrefix[0] = 0x04;
            System.arraycopy(pubUncompressed, 0, withPrefix, 1, 64);
            ECPoint point = SM2_DOMAIN.getCurve().decodePoint(withPrefix);
            return new ECPublicKeyParameters(point, SM2_DOMAIN);
        } else {
            throw new IllegalArgumentException(
                    "SM2 公钥长度非法，必须 64 或 65 字节，实际=" + pubUncompressed.length);
        }
    }

    /**
     * 解析 ASN.1 DER 编码的 SM2 签名 (r, s)
     *
     * <p>DER 结构：SEQUENCE { INTEGER r, INTEGER s }
     */
    private static Sm2Signature parseDerSignature(byte[] der) {
        try {
            org.bouncycastle.asn1.ASN1Sequence seq = org.bouncycastle.asn1.ASN1Sequence.getInstance(der);
            org.bouncycastle.asn1.ASN1Integer rInt = (org.bouncycastle.asn1.ASN1Integer) seq.getObjectAt(0);
            org.bouncycastle.asn1.ASN1Integer sInt = (org.bouncycastle.asn1.ASN1Integer) seq.getObjectAt(1);
            return new Sm2Signature(rInt.getValue(), sInt.getValue());
        } catch (Exception e) {
            throw new RuntimeException("解析 SM2 DER 签名失败", e);
        }
    }

    /**
     * ASN.1 DER 编码 (r, s)
     */
    private static byte[] encodeDerSignature(BigInteger r, BigInteger s) {
        org.bouncycastle.asn1.ASN1EncodableVector v = new org.bouncycastle.asn1.ASN1EncodableVector();
        v.add(new org.bouncycastle.asn1.ASN1Integer(r));
        v.add(new org.bouncycastle.asn1.ASN1Integer(s));
        try {
            return new org.bouncycastle.asn1.DERSequence(v).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("编码 SM2 DER 签名失败", e);
        }
    }
}
