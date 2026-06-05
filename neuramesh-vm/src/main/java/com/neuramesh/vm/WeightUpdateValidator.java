package com.neuramesh.vm;

import com.neuramesh.consensus.bft.Validator;
import com.neuramesh.consensus.bft.ValidatorSet;
import com.neuramesh.core.CryptoUtils;
import com.neuramesh.vm.payload.WeightUpdatePayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权重更新交叉验证器。
 *
 * <p>规则：从负载提取见证 {@link Attestation}，逐个验签（来自 {@link ValidatorSet} 中不同验证者）。
 * 在「有效且来自不同验证者」的见证里，若存在某一声明分数被 &ge; 2 个见证一致背书，则通过，
 * 并返回该一致分数及偏差见证者列表；否则拒绝。
 */
public final class WeightUpdateValidator {

    /** 分数一致性比较容差。 */
    public static final double SCORE_EPSILON = 1e-9;

    private final ValidatorSet validators;

    public WeightUpdateValidator(ValidatorSet validators) {
        this.validators = java.util.Objects.requireNonNull(validators, "validators");
    }

    /**
     * 交叉验证结果。
     *
     * @param accepted              是否通过
     * @param agreedScore           达成一致的分数（accepted 时有效）
     * @param deviatingValidatorIds 有效但分数偏离一致值的见证者 hex 列表
     */
    public record Result(boolean accepted, double agreedScore, List<String> deviatingValidatorIds) {
        public static Result reject() {
            return new Result(false, 0.0, List.of());
        }
    }

    /**
     * 执行交叉验证。
     *
     * @param payload 权重更新负载
     * @return 验证结果
     */
    public Result validate(WeightUpdatePayload payload) {
        byte[] target = payload.targetNodeId();
        // 1) 过滤：签名有效 + 验证者存在 + 验证者去重
        Map<String, Attestation> validByValidator = new LinkedHashMap<>();
        for (Attestation a : payload.attestations()) {
            Validator v = validators.getByNodeId(a.validatorId());
            if (v == null) {
                continue;
            }
            byte[] signing = Attestation.signingBytes(target, a.claimedScore());
            if (!CryptoUtils.verify(signing, a.signature(), v.getPublicKey())) {
                continue;
            }
            validByValidator.putIfAbsent(a.validatorIdHex(), a);
        }
        if (validByValidator.size() < 2) {
            return Result.reject();
        }
        // 2) 按声明分数分组，寻找 >= 2 个一致的分数
        List<Attestation> valid = new ArrayList<>(validByValidator.values());
        for (Attestation candidate : valid) {
            List<Attestation> agree = new ArrayList<>();
            for (Attestation other : valid) {
                if (Math.abs(other.claimedScore() - candidate.claimedScore()) <= SCORE_EPSILON) {
                    agree.add(other);
                }
            }
            if (agree.size() >= 2) {
                double agreed = candidate.claimedScore();
                List<String> deviating = new ArrayList<>();
                for (Attestation a : valid) {
                    if (Math.abs(a.claimedScore() - agreed) > SCORE_EPSILON) {
                        deviating.add(a.validatorIdHex());
                    }
                }
                return new Result(true, agreed, deviating);
            }
        }
        return Result.reject();
    }
}
