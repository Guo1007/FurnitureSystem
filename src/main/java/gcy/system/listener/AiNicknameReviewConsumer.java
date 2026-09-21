package gcy.system.listener;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.model.chat.ChatModel;
import gcy.system.entity.pojo.NicknameReviewLog;
import gcy.system.entity.pojo.User;
import gcy.system.mapper.NicknameReviewLogMapper;
import gcy.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 昵称 AI 自动审核消费者。
 * <p>
 * 监听昵称审核消息，调用 AI 大模型审核用户昵称是否合规。
 * AI 判定通过则直接设为 nicknameReviewStatus=0，将 pendingNickname 写入 userName；
 * AI 判定不通过则设为 nicknameReviewStatus=3（待人工复审）。
 * </p>
 *
 * @author 郭名城
 * @date 2026-08-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "nickname-review-topic",
        consumerGroup = "nickname-ai-review-consumer",
        consumeThreadMax = 20,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class AiNicknameReviewConsumer implements RocketMQListener<String> {

    private final ChatModel openAiChatModel;

    private final UserMapper userMapper;

    private final NicknameReviewLogMapper nicknameReviewLogMapper;

    @Override
    public void onMessage(String message) {
        Map<String, Object> msg;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JSONUtil.toBean(message, Map.class);
            msg = parsed;
        } catch (Exception e) {
            log.error("昵称审核消息解析失败: {}", message, e);
            return;
        }

        Long userId = Long.valueOf(msg.get("userId").toString());
        String nickname = (String) msg.get("nickname");

        User user = userMapper.selectById(userId);
        if (user == null || user.getNicknameReviewStatus() != 1) {
            return; // 幂等：已处理过或状态不对
        }

        NicknameReviewResult result = aiReviewNickname(nickname);
        if (result.pass) {
            // AI 审核通过：更新 user 表 + log 表
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getUserName, nickname)
                    .set(User::getPendingNickname, null)
                    .set(User::getNicknameReviewStatus, 0));
            nicknameReviewLogMapper.update(null, new LambdaUpdateWrapper<NicknameReviewLog>()
                    .eq(NicknameReviewLog::getUserId, userId)
                    .eq(NicknameReviewLog::getStatus, 1)
                    .set(NicknameReviewLog::getStatus, 0));
            log.info("AI审核昵称通过: userId={}, nickname={}", userId, nickname);
        } else {
            // AI 审核不通过：更新 user 表 + log 表
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getNicknameReviewStatus, 3));
            nicknameReviewLogMapper.update(null, new LambdaUpdateWrapper<NicknameReviewLog>()
                    .eq(NicknameReviewLog::getUserId, userId)
                    .eq(NicknameReviewLog::getStatus, 1)
                    .set(NicknameReviewLog::getStatus, 3)
                    .set(NicknameReviewLog::getAiRejectReason, result.rejectReason));
            log.info("AI审核昵称不通过，待人工复审: userId={}, nickname={}, reason={}", userId, nickname, result.rejectReason);
        }
    }

    /**
     * 调用 AI 审核昵称。
     *
     * @param nickname 待审核的昵称
     * @return true=通过，false=需人工复审
     */
    private NicknameReviewResult aiReviewNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return new NicknameReviewResult(true, null);
        }
        String prompt = buildNicknameReviewPrompt(nickname);
        try {
            String response = openAiChatModel.chat(prompt);
            log.debug("AI昵称审核原始响应: {}", response);
            if (response != null && response.trim().startsWith("PASS")) {
                return new NicknameReviewResult(true, null);
            }
            String reason = response != null && response.length() > 4 ? response.substring(4).trim() : null;
            if (reason != null && reason.startsWith(":")) {
                reason = reason.substring(1).trim();
            }
            if (reason == null || reason.isEmpty()) {
                reason = "昵称不符合审核规则";
            }
            return new NicknameReviewResult(false, reason);
        } catch (Exception e) {
            log.error("AI昵称审核调用失败，昵称: {}", nickname, e);
            throw new RuntimeException("AI昵称审核调用失败", e);
        }
    }

    private record NicknameReviewResult(boolean pass, String rejectReason) {
    }

    /**
     * 构建昵称审核提示词。
     */
    private String buildNicknameReviewPrompt(String nickname) {
        return """
                你是一个电商平台的用户昵称审核员。请审核以下用户昵称是否合规。
                
                审核规则：
                1. 包含广告、推广信息、联系方式（手机号、微信号、QQ号等）→ 不通过
                2. 包含辱骂、人身攻击、色情、政治敏感内容 → 不通过
                3. 包含"管理员"、"客服"、"官方"等冒充官方身份的词汇 → 不通过
                4. 包含谐音、变体形式的违规内容 → 不通过
                5. 正常的个性化昵称 → 通过
                
                输出格式：
                - 如果通过，只输出：PASS
                - 如果不通过，输出：FAIL: 拒绝原因（一句话简要说明）
                
                待审核昵称：
                %s
                """.formatted(nickname);
    }
}