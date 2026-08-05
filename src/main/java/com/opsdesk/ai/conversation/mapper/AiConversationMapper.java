package com.opsdesk.ai.conversation.mapper;

import com.opsdesk.ai.conversation.model.ConversationRow;
import com.opsdesk.ai.conversation.model.MessageRow;
import com.opsdesk.ai.conversation.model.ReferenceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** AI 会话、消息、引用和反馈的数据访问入口，所有查询 SQL 均在 XML 中维护。 */
@Mapper
public interface AiConversationMapper {
    int insertConversation(ConversationRow row);
    ConversationRow selectOwnedConversation(@Param("id") long id, @Param("ownerId") long ownerId);
    List<ConversationRow> searchOwned(@Param("ownerId") long ownerId, @Param("keyword") String keyword,
                                      @Param("status") String status);
    int updateConversationActivity(@Param("id") long id, @Param("ownerId") long ownerId,
                                   @Param("messageCount") int messageCount);
    int updateConversationStatus(@Param("id") long id, @Param("ownerId") long ownerId, @Param("status") String status);
    int logicalDeleteConversation(@Param("id") long id, @Param("ownerId") long ownerId);
    int logicalDeleteMessages(@Param("conversationId") long conversationId, @Param("ownerId") long ownerId);
    int insertMessage(MessageRow row);
    int nextSequence(@Param("conversationId") long conversationId);
    List<MessageRow> selectMessages(@Param("conversationId") long conversationId, @Param("ownerId") long ownerId);
    List<MessageRow> selectRecentMessages(@Param("conversationId") long conversationId, @Param("limit") int limit);
    int completeMessage(@Param("id") long id, @Param("content") String content, @Param("contentHash") String contentHash,
                        @Param("callLogId") long callLogId, @Param("insufficient") boolean insufficient);
    int failMessage(@Param("id") long id, @Param("status") String status);
    MessageRow selectOwnedAssistantMessage(@Param("id") long id, @Param("ownerId") long ownerId);
    int insertReference(ReferenceRow row);
    List<ReferenceRow> selectReferences(@Param("callLogId") long callLogId);
    int upsertFeedback(@Param("id") long id, @Param("messageId") long messageId, @Param("callLogId") long callLogId,
                       @Param("operatorId") long operatorId, @Param("rating") String rating,
                       @Param("reasonCode") String reasonCode, @Param("comment") String comment);
}
