package com.opsdesk.ai.conversation.service;

import com.opsdesk.ai.common.exception.BusinessException;
import com.opsdesk.ai.common.id.LocalSnowflakeIdGenerator;
import com.opsdesk.ai.conversation.mapper.AiConversationMapper;
import com.opsdesk.ai.conversation.model.ConversationRow;
import com.opsdesk.ai.conversation.model.MessageRow;
import com.opsdesk.ai.rag.PromptSanitizer;
import com.opsdesk.ai.security.ServicePrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/** AI 会话服务资源范围和首轮消息落库测试。 */
class AiConversationServiceTest {

    @Test
    void shouldCreateConversationAndTwoMessagesForFirstQuestion() {
        AiConversationMapper mapper = mock(AiConversationMapper.class);
        LocalSnowflakeIdGenerator ids = mock(LocalSnowflakeIdGenerator.class);
        when(ids.nextId()).thenReturn(101L, 102L, 103L);
        when(mapper.nextSequence(101L)).thenReturn(1);
        AiConversationService service = new AiConversationService(mapper, ids, new PromptSanitizer());

        AiConversationService.SessionContext context = service.begin(
                new ServicePrincipal("opsdesk-backend", "7", List.of("USER"), "token"), null, "VPN 超时怎么办");

        assertEquals(101L, context.conversationId());
        assertEquals(103L, context.messageId());
        InOrder order = inOrder(mapper);
        order.verify(mapper).insertConversation(any(ConversationRow.class));
        order.verify(mapper, times(2)).insertMessage(any(MessageRow.class));
        order.verify(mapper).updateConversationActivity(101L, 7L, 2);
    }

    @Test
    void shouldRejectConversationThatDoesNotBelongToCurrentUser() {
        AiConversationMapper mapper = mock(AiConversationMapper.class);
        AiConversationService service = new AiConversationService(mapper, mock(LocalSnowflakeIdGenerator.class),
                new PromptSanitizer());
        when(mapper.selectOwnedConversation(88L, 7L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.begin(
                new ServicePrincipal("opsdesk-backend", "7", List.of("USER"), "token"), "88", "继续追问"));
        verify(mapper, never()).insertMessage(any(MessageRow.class));
        verify(mapper, never()).updateConversationActivity(anyLong(), anyLong(), anyInt());
    }
}
