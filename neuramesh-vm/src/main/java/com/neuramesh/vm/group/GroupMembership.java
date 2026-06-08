package com.neuramesh.vm.group;

import com.neuramesh.vm.exception.VMException;

/**
 * 节点与资源组的成员关系。
 *
 * <p>记录某节点加入的组、加入时间与软性验证状态（{@link GroupValidator} 的结果）。
 * 一个节点同一时刻只属于一个组（再次加入会覆盖旧关系）。
 *
 * @param nodeIdHex              节点地址 hex
 * @param groupId                所属资源组 id
 * @param joinedAt               加入时间戳（毫秒）
 * @param verified               是否通过软性加入验证
 * @param membershipCertificate  平台签发的成员凭证（对 nodeId+groupId 的平台签名 hex，空串表示未签发）
 */
public record GroupMembership(String nodeIdHex, String groupId, long joinedAt, boolean verified,
                              String membershipCertificate) {

    public GroupMembership {
        if (nodeIdHex == null || nodeIdHex.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "nodeIdHex 不可为空");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new VMException(VMException.Kind.INVALID_PAYLOAD, "groupId 不可为空");
        }
        membershipCertificate = membershipCertificate == null ? "" : membershipCertificate;
    }

    /** 向后兼容构造器：无成员凭证。 */
    public GroupMembership(String nodeIdHex, String groupId, long joinedAt, boolean verified) {
        this(nodeIdHex, groupId, joinedAt, verified, "");
    }

    /** 深拷贝（record 不可变，直接返回自身）。 */
    public GroupMembership copy() {
        return this;
    }
}
