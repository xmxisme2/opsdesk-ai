package com.opsdesk.ai.conversation.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.common.id.LocalSnowflakeIdGenerator;
import com.opsdesk.ai.conversation.dto.ConversationSearchRequest;
import com.opsdesk.ai.conversation.dto.FeedbackRequest;
import com.opsdesk.ai.conversation.mapper.AiConversationMapper;
import com.opsdesk.ai.conversation.model.ConversationRow;
import com.opsdesk.ai.conversation.model.MessageRow;
import com.opsdesk.ai.conversation.model.ReferenceRow;
import com.opsdesk.ai.conversation.vo.ConversationActionVO;
import com.opsdesk.ai.conversation.vo.ConversationDetailVO;
import com.opsdesk.ai.conversation.vo.ConversationVO;
import com.opsdesk.ai.conversation.vo.MessageVO;
import com.opsdesk.ai.conversation.vo.PageResult;
import com.opsdesk.ai.knowledge.search.KnowledgeSearchHit;
import com.opsdesk.ai.rag.PromptSanitizer;
import com.opsdesk.ai.rag.vo.RagReferenceVO;
import com.opsdesk.ai.security.ServicePrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/** AI 会话领域服务，集中处理所有者校验、消息持久化、保留期、引用和反馈。 */
@Service
public class AiConversationService {
    /** 多轮上下文最多取最近 10 条已完成消息，避免 Prompt 无界增长。 */
    private static final int CONTEXT_MESSAGE_LIMIT = 10;
    /** 会话默认保留 30 天。 */
    private static final int RETENTION_DAYS = 30;
    private final AiConversationMapper mapper;
    private final LocalSnowflakeIdGenerator idGenerator;
    private final PromptSanitizer sanitizer;

    public AiConversationService(AiConversationMapper mapper, LocalSnowflakeIdGenerator idGenerator,
                                 PromptSanitizer sanitizer) {
        this.mapper = mapper;
        this.idGenerator = idGenerator;
        this.sanitizer = sanitizer;
    }

    /** 为一次问答创建用户消息和待完成助手消息，并返回受控多轮上下文。 */
    @Transactional
    public SessionContext begin(ServicePrincipal principal, String conversationId, String question) {
        long ownerId = ownerId(principal);
        String safeQuestion = sanitizer.sanitize(question.trim());
        ConversationRow conversation;
        List<MessageRow> previousMessages;
        if (conversationId == null || conversationId.isBlank()) {
            conversation = new ConversationRow();
            conversation.setId(idGenerator.nextId());
            conversation.setOwnerId(ownerId);
            conversation.setTitle(titleOf(safeQuestion));
            conversation.setScene("KNOWLEDGE_RAG");
            conversation.setStatus("ACTIVE");
            conversation.setLastMessageTime(LocalDateTime.now());
            conversation.setMessageCount(0);
            conversation.setExpireTime(LocalDateTime.now().plusDays(RETENTION_DAYS));
            mapper.insertConversation(conversation);
            previousMessages = List.of();
        } else {
            conversation = ownedConversation(parseId(conversationId), ownerId);
            if (!"ACTIVE".equals(conversation.getStatus())) {
                throw new BusinessException(ErrorCode.AI_RESOURCE_FORBIDDEN, "归档会话不能继续提问");
            }
            previousMessages = mapper.selectRecentMessages(conversation.getId(), CONTEXT_MESSAGE_LIMIT);
        }
        int sequence = mapper.nextSequence(conversation.getId());
        MessageRow user = message(conversation.getId(), ownerId, "USER", safeQuestion, sequence, "SUCCESS");
        MessageRow assistant = message(conversation.getId(), ownerId, "ASSISTANT", "", sequence + 1, "PENDING");
        mapper.insertMessage(user);
        mapper.insertMessage(assistant);
        int count = (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 2;
        mapper.updateConversationActivity(conversation.getId(), ownerId, count);
        return new SessionContext(conversation.getId(), assistant.getId(), historyPrompt(previousMessages));
    }

    /** 正常结束后保存助手回答、审计关联和检索引用快照。 */
    @Transactional
    public void complete(SessionContext context, String answer, boolean insufficient, long callLogId,
                         List<KnowledgeSearchHit> hits) {
        String safeAnswer = sanitizer.sanitize(answer);
        mapper.completeMessage(context.messageId(), safeAnswer, sha256(safeAnswer), callLogId, insufficient);
        int rank = 1;
        for (KnowledgeSearchHit hit : hits) {
            ReferenceRow row = new ReferenceRow();
            row.setId(idGenerator.nextId());
            row.setCallLogId(callLogId);
            row.setArticleId(Long.parseLong(hit.articleId()));
            row.setArticleVersion(hit.articleVersion());
            row.setChunkId(hit.chunkId());
            row.setChunkNo(rank - 1);
            row.setTitle(sanitizer.sanitize(hit.title()));
            row.setHeading(sanitizer.sanitize(hit.heading()));
            String content = sanitizer.sanitize(hit.content());
            row.setSnippet(content.substring(0, Math.min(300, content.length())));
            row.setKeywordScore(hit.keywordScore());
            row.setVectorScore(hit.vectorScore());
            row.setFinalScore(hit.score());
            row.setRankNo(rank++);
            mapper.insertReference(row);
        }
    }

    /** 生成失败或用户停止时保留可追溯状态，不把未完成内容当作成功回答。 */
    public void fail(SessionContext context, boolean cancelled) {
        mapper.failMessage(context.messageId(), cancelled ? "CANCELLED" : "FAILED");
    }

    public PageResult<ConversationVO> search(ServicePrincipal principal, ConversationSearchRequest request) {
        long ownerId = ownerId(principal);
        String status = Boolean.TRUE.equals(request.archived()) ? "ARCHIVED" : "ACTIVE";
        PageHelper.startPage(request.normalizedPage(), request.normalizedSize());
        try {
            List<ConversationRow> rows = mapper.searchOwned(ownerId, trimToNull(request.keyword()), status);
            PageInfo<ConversationRow> page = new PageInfo<>(rows);
            return new PageResult<>(rows.stream().map(this::toVO).toList(), page.getPageNum(), page.getPageSize(), page.getTotal());
        } finally {
            PageHelper.clearPage();
        }
    }

    public ConversationDetailVO detail(ServicePrincipal principal, String id) {
        long ownerId = ownerId(principal);
        ConversationRow conversation = ownedConversation(parseId(id), ownerId);
        List<MessageVO> messages = mapper.selectMessages(conversation.getId(), ownerId).stream().map(this::toMessageVO).toList();
        return new ConversationDetailVO(toVO(conversation), messages);
    }

    public ConversationActionVO archive(ServicePrincipal principal, String id) {
        long ownerId = ownerId(principal);
        long parsedId = parseId(id);
        ownedConversation(parsedId, ownerId);
        mapper.updateConversationStatus(parsedId, ownerId, "ARCHIVED");
        return new ConversationActionVO(id, "ARCHIVED");
    }

    @Transactional
    public ConversationActionVO delete(ServicePrincipal principal, String id) {
        long ownerId = ownerId(principal);
        long parsedId = parseId(id);
        ownedConversation(parsedId, ownerId);
        mapper.logicalDeleteMessages(parsedId, ownerId);
        mapper.logicalDeleteConversation(parsedId, ownerId);
        return new ConversationActionVO(id, "DELETED");
    }

    public void feedback(ServicePrincipal principal, String messageId, FeedbackRequest request) {
        long ownerId = ownerId(principal);
        MessageRow message = mapper.selectOwnedAssistantMessage(parseId(messageId), ownerId);
        if (message == null || message.getCallLogId() == null || !"SUCCESS".equals(message.getStatus())) {
            throw new BusinessException(ErrorCode.AI_RESOURCE_FORBIDDEN, "无权评价该 AI 回答");
        }
        if (request.rating() == FeedbackRequest.Rating.DOWN && request.reasonCode() == null) {
            throw new BusinessException(ErrorCode.AI_REQUEST_INVALID, "点踩时请选择原因");
        }
        mapper.upsertFeedback(idGenerator.nextId(), message.getId(), message.getCallLogId(), ownerId,
                request.rating().name(), request.reasonCode() == null ? null : request.reasonCode().name(),
                sanitizer.sanitize(request.comment()));
    }

    private MessageVO toMessageVO(MessageRow row) {
        List<RagReferenceVO> references = row.getCallLogId() == null ? List.of() : mapper.selectReferences(row.getCallLogId()).stream()
                .map(ref -> new RagReferenceVO(String.valueOf(ref.getArticleId()), ref.getTitle(), ref.getHeading(),
                        ref.getSnippet(), ref.getFinalScore() == null ? 0D : ref.getFinalScore())).toList();
        return new MessageVO(String.valueOf(row.getId()), row.getRole(), row.getContent(), row.getStatus(),
                Boolean.TRUE.equals(row.getInsufficientEvidence()), row.getFeedback(), row.getCreateTime(), references);
    }

    private ConversationVO toVO(ConversationRow row) {
        return new ConversationVO(String.valueOf(row.getId()), row.getTitle(), row.getStatus(),
                row.getMessageCount() == null ? 0 : row.getMessageCount(), row.getLastMessageTime(), row.getCreateTime());
    }

    private MessageRow message(long conversationId, long ownerId, String role, String content, int sequence, String status) {
        MessageRow row = new MessageRow();
        row.setId(idGenerator.nextId());
        row.setConversationId(conversationId);
        row.setRole(role);
        row.setContent(content);
        row.setContentHash(sha256(content));
        row.setSequenceNo(sequence);
        row.setInsufficientEvidence(false);
        row.setStatus(status);
        row.setCreateBy(ownerId);
        return row;
    }

    private String historyPrompt(List<MessageRow> messages) {
        if (messages.isEmpty()) return "";
        StringBuilder prompt = new StringBuilder("\n\n最近会话（仅作上下文，仍须依据本次知识片段回答）：\n");
        for (MessageRow message : messages) {
            prompt.append("USER".equals(message.getRole()) ? "用户：" : "助手：")
                    .append(message.getContent()).append('\n');
        }
        return prompt.toString();
    }

    private ConversationRow ownedConversation(long id, long ownerId) {
        ConversationRow row = mapper.selectOwnedConversation(id, ownerId);
        if (row == null) throw new BusinessException(ErrorCode.AI_RESOURCE_FORBIDDEN, "无权访问该 AI 会话");
        return row;
    }

    private long ownerId(ServicePrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId().isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少用户上下文");
        }
        return parseId(principal.userId());
    }

    private long parseId(String value) {
        try { return Long.parseLong(value); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.AI_RESOURCE_FORBIDDEN, "AI 资源 ID 无效"); }
    }

    private String titleOf(String question) { return question.length() <= 30 ? question : question.substring(0, 30) + "…"; }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法计算消息摘要", exception); }
    }

    /** 单次问答持久化上下文，供 JSON 与 SSE 编排共用。 */
    public record SessionContext(long conversationId, long messageId, String historyPrompt) { }
}
