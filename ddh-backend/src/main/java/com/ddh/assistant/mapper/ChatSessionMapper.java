package com.ddh.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddh.assistant.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
