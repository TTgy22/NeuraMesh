package com.neuramesh.vm.group;

/**
 * 资源组软性加入验证器。
 *
 * <p>检查节点是否满足某资源组的加入要求：
 * <ol>
 *   <li>性能门槛：节点 benchmark 分数 ≥ 组内最低分；</li>
 *   <li>网络要求：HTTP/2.0 支持（握手检测）；</li>
 *   <li>地区要求：GPS 定位校验；</li>
 *   <li>防代理：IP 归属地校验。</li>
 * </ol>
 *
 * <p>本 Pause 仅实现性能门槛的真实校验；HTTP/2 握手、GPS 与 IP 归属地为接口占位，统一返回
 * {@code true}，真实验证留到 P6/赛后。
 */
public final class GroupValidator {

    /**
     * 验证结果。
     *
     * @param passed 是否通过全部软性要求
     * @param reason 未通过时的原因（通过时为空串）
     */
    public record Result(boolean passed, String reason) {
        public static Result ok() {
            return new Result(true, "");
        }

        public static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * 综合验证节点能否加入资源组。
     *
     * @param group          目标资源组
     * @param benchmarkScore 节点 benchmark 分数
     * @param supportsHttp2  节点是否声明支持 HTTP/2.0（由握手检测得出）
     * @return 验证结果
     */
    public Result validate(ResourceGroup group, double benchmarkScore, boolean supportsHttp2) {
        if (benchmarkScore < group.minBenchmarkScore()) {
            return Result.fail(String.format(
                    "benchmark 分数 %.2f 低于组门槛 %.2f", benchmarkScore, group.minBenchmarkScore()));
        }
        if (group.requiredHttp2() && !checkHttp2(supportsHttp2)) {
            return Result.fail("节点不支持 HTTP/2.0");
        }
        if (!checkGps()) {
            return Result.fail("GPS 地区校验未通过");
        }
        if (!checkIpReputation()) {
            return Result.fail("IP 归属地校验未通过（疑似代理）");
        }
        return Result.ok();
    }

    /**
     * HTTP/2.0 握手检测。
     *
     * @param supportsHttp2 调用方握手探测结果
     * @return 是否支持
     */
    boolean checkHttp2(boolean supportsHttp2) {
        // TODO: P6 实现真实 HTTP/2.0 握手检测（当前直接采用调用方探测值）
        return supportsHttp2;
    }

    /** GPS 地区定位校验（接口占位）。 */
    boolean checkGps() {
        // TODO: P6 实现真实 GPS 定位校验
        return true;
    }

    /** IP 归属地/防代理校验（接口占位）。 */
    boolean checkIpReputation() {
        // TODO: P6 实现真实 IP 归属地校验
        return true;
    }
}
