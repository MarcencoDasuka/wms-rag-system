package com.isd.wms.mapper;

import com.isd.wms.dto.user.UserResponse;
import com.isd.wms.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getUserRole(),
            user.getIsActive()
        );
    }
}
