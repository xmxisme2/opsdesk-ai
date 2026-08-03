package com.opsdesk.ai.security;

import java.util.List;

/**
 * 主应用签名后传入的服务身份和用户上下文。
 *
 * @param serviceName 调用方服务名
 * @param userId      可选的 OpsDesk 用户 ID
 * @param roles       用户角色快照
 * @param tokenId     JWT 唯一 ID
 */
public record ServicePrincipal(String serviceName, String userId, List<String> roles, String tokenId) {
}
