package com.neuramesh.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 简化版 Merkle Tree。
 *
 * <p>构建规则：
 * <ul>
 *   <li>叶子 = SHA-256(原始数据)。</li>
 *   <li>每层从左到右两两配对求父哈希 = SHA-256(left || right)；若某层节点数为奇数，最后一个节点与自身配对。</li>
 *   <li>逐层向上直到只剩一个根节点。</li>
 * </ul>
 *
 * <p>本类为不可变对象：构造完成后内部各层哈希不再变化，对外返回的字节数组均为防御性拷贝。
 */
public final class MerkleTree {

    /**
     * Merkle 证明节点。
     *
     * @param hash        兄弟节点哈希
     * @param leftSibling 兄弟节点是否位于左侧（true 表示拼接顺序为 sibling || current）
     */
    public record ProofNode(byte[] hash, boolean leftSibling) {

        public ProofNode {
            if (hash == null) {
                throw new NeuraException("证明节点哈希不可为 null");
            }
            hash = hash.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }

    /** 各层哈希，levels[0] 为叶子层，levels[last] 为根层。 */
    private final List<List<byte[]>> levels;

    private final int leafCount;

    /**
     * 由原始数据列表构建 Merkle Tree。
     *
     * @param data 原始数据列表（不可为 null，元素不可为 null）；允许为空
     */
    public MerkleTree(List<byte[]> data) {
        if (data == null) {
            throw new NeuraException("Merkle 数据列表不可为 null");
        }
        this.leafCount = data.size();
        this.levels = new ArrayList<>();

        List<byte[]> leaves = new ArrayList<>(data.size());
        for (byte[] item : data) {
            if (item == null) {
                throw new NeuraException("Merkle 叶子数据不可为 null");
            }
            leaves.add(CryptoUtils.sha256(item));
        }
        if (leaves.isEmpty()) {
            // 空树根定义为 SHA-256(空字节)，保证确定性且非 null
            leaves.add(CryptoUtils.sha256(new byte[0]));
        }
        levels.add(leaves);

        List<byte[]> current = leaves;
        while (current.size() > 1) {
            List<byte[]> parents = new ArrayList<>((current.size() + 1) / 2);
            for (int i = 0; i < current.size(); i += 2) {
                byte[] left = current.get(i);
                byte[] right = (i + 1 < current.size()) ? current.get(i + 1) : current.get(i);
                parents.add(CryptoUtils.sha256(left, right));
            }
            levels.add(parents);
            current = parents;
        }
    }

    /**
     * 获取根哈希。
     *
     * @return 32 字节根哈希（防御性拷贝）
     */
    public byte[] getRoot() {
        List<byte[]> top = levels.get(levels.size() - 1);
        return top.get(0).clone();
    }

    /**
     * 叶子数量（不含空树补齐）。
     *
     * @return 原始叶子数量
     */
    public int getLeafCount() {
        return leafCount;
    }

    /**
     * 生成指定叶子的 Merkle 证明路径。
     *
     * @param index 叶子下标（0 起）
     * @return 自底向上的兄弟节点列表
     */
    public List<ProofNode> getProof(int index) {
        if (index < 0 || index >= leafCount) {
            throw new NeuraException("叶子下标越界: " + index + "，叶子数=" + leafCount);
        }
        List<ProofNode> proof = new ArrayList<>();
        int idx = index;
        for (int level = 0; level < levels.size() - 1; level++) {
            List<byte[]> nodes = levels.get(level);
            boolean isRightNode = (idx & 1) == 1;
            int siblingIndex = isRightNode ? idx - 1 : idx + 1;
            // 奇数层最后节点与自身配对的情况，兄弟即自身
            if (siblingIndex >= nodes.size()) {
                siblingIndex = idx;
            }
            proof.add(new ProofNode(nodes.get(siblingIndex), isRightNode));
            idx /= 2;
        }
        return Collections.unmodifiableList(proof);
    }

    /**
     * 校验某段原始数据是否属于给定根哈希。
     *
     * @param data  原始叶子数据
     * @param proof {@link #getProof(int)} 返回的证明路径
     * @param root  期望的根哈希
     * @return 校验是否通过
     */
    public static boolean verifyProof(byte[] data, List<ProofNode> proof, byte[] root) {
        if (data == null || proof == null || root == null) {
            throw new NeuraException("verifyProof 参数不可为 null");
        }
        byte[] hash = CryptoUtils.sha256(data);
        for (ProofNode node : proof) {
            byte[] sibling = node.hash();
            if (node.leftSibling()) {
                hash = CryptoUtils.sha256(sibling, hash);
            } else {
                hash = CryptoUtils.sha256(hash, sibling);
            }
        }
        return Arrays.equals(hash, root);
    }
}
