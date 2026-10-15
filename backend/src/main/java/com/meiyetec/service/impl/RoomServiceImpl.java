package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.Room;
import com.meiyetec.mapper.RoomMapper;
import com.meiyetec.service.RoomService;
import org.springframework.stereotype.Service;

@Service
public class RoomServiceImpl extends ServiceImpl<RoomMapper, Room> implements RoomService {
}