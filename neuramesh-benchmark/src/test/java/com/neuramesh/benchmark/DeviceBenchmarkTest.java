package com.neuramesh.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class DeviceBenchmarkTest {

    @Test
    @Timeout(60)
    @DisplayName("同一设备多次 Benchmark：分数为正且可重复运行")
    void repeated_benchmark_runs() {
        DeviceBenchmark bench = new DeviceBenchmark(2000);
        BenchmarkResult r1 = bench.run("test-device");
        BenchmarkResult r2 = bench.run("test-device");
        assertThat(r1.score()).isPositive();
        assertThat(r2.score()).isPositive();
        assertThat(r1.cpuCores()).isPositive();
        assertThat(r1.deviceModel()).isEqualTo("test-device");
        // 耗时为非负
        assertThat(r1.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("指纹生成与校验：未篡改通过，篡改后失败")
    void fingerprint_verify() {
        BenchmarkResult result = new BenchmarkResult(5000.0, 12, "deviceA", 8, 16384, 1L);
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        Fingerprint fp = Fingerprint.generate(result, salt);
        assertThat(fp.verify(result)).isTrue();
        assertThat(fp.getHash()).hasSize(32);

        // 篡改分数后校验失败
        BenchmarkResult tampered = new BenchmarkResult(9999.0, 12, "deviceA", 8, 16384, 1L);
        assertThat(fp.verify(tampered)).isFalse();
    }

    @Test
    @DisplayName("不同设备画像产生不同指纹")
    void different_devices_distinct_fingerprints() {
        byte[] salt = new byte[] {1, 2, 3, 4};
        BenchmarkResult a = new BenchmarkResult(5000.0, 10, "deviceA", 8, 16384, 1L);
        BenchmarkResult b = new BenchmarkResult(5000.0, 10, "deviceB", 4, 8192, 1L);
        Fingerprint fpA = Fingerprint.generate(a, salt);
        Fingerprint fpB = Fingerprint.generate(b, salt);
        assertThat(fpA.getHashHex()).isNotEqualTo(fpB.getHashHex());
    }

    @Test
    @DisplayName("分数与设备画像绑定：相同输入相同指纹（确定性）")
    void deterministic_fingerprint() {
        byte[] salt = new byte[] {9, 9, 9};
        BenchmarkResult r = new BenchmarkResult(1234.5, 7, "dev", 2, 4096, 1L);
        assertThat(Fingerprint.generate(r, salt).getHashHex())
                .isEqualTo(Fingerprint.generate(r, salt).getHashHex());
    }
}
