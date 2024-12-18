package com.project.shopapp.services;

import com.project.shopapp.components.JwtTokenUtils;
import com.project.shopapp.components.LocalizationUtils;
import com.project.shopapp.dtos.UserDTO;
import com.project.shopapp.exceptions.DataNotFoundException;
import com.project.shopapp.exceptions.PermissionDenyException;
import com.project.shopapp.models.Role;
import com.project.shopapp.models.User;
import com.project.shopapp.repositories.RoleRepository;
import com.project.shopapp.repositories.UserRepository;
import com.project.shopapp.utils.MessageKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserService implements IUserService{
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtils jwtTokenUtil;
    private final AuthenticationManager authenticationManager;
    private final LocalizationUtils localizationUtils;
    @Override
    public User createUser(UserDTO userDTO) throws Exception {
        String phoneNumber = userDTO.getPhoneNumber();
        // checking with phonenumber existing or not
        if(userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DataIntegrityViolationException("Phone number already exists");
        }
        Role role =roleRepository.findById(userDTO.getRoleId())
                .orElseThrow(() -> new DataNotFoundException("Role not found"));
        if(role.getName().toUpperCase().equals(Role.ADMIN)){
            throw new PermissionDenyException("you cannot register a admin account");
        }
        //convert from userDTO => user
        User newUser = User.builder()
                .fullName(userDTO.getFullName())
                .phoneNumber(userDTO.getPhoneNumber())
                .password(userDTO.getPassword())
                .address(userDTO.getAddress())
                .dateOfBirth(userDTO.getDateOfBirth())
                .facebookAccountId(userDTO.getFacebookAccountId())
                .googleAccountId(userDTO.getGoogleAccountId())
                .build();

        newUser.setRole(role);
        // Kiểm tra nếu có accountId, không yêu cầu password
        if (userDTO.getFacebookAccountId() == 0 && userDTO.getGoogleAccountId() == 0) {
            String password = userDTO.getPassword();
            String encodedPassword = passwordEncoder.encode(password);
            newUser.setPassword(encodedPassword);
        }
        return userRepository.save(newUser);
    }

//    @Override
//    public String login(String phoneNumber, String password) throws Exception{
//        Optional<User> optionalUser = userRepository.findByPhoneNumber(phoneNumber);
//        if (optionalUser.isEmpty()){
//            throw new DataNotFoundException("login failed. Please try again");
//        }
//        //return optionalUser.get();
//        //want return JWT token?
//        User existingUser = optionalUser.get();
//        //check password
//        if (existingUser.getFacebookAccountId() == 0 && existingUser.getGoogleAccountId() == 0){
//            if(!passwordEncoder.matches(password,existingUser.getPassword())){
//                throw new BadCredentialsException("Wrong phone number or password");
//            }
//        }
//        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken
//                (phoneNumber,password,existingUser.getAuthorities());
//        //authenticate with Java Spring Security
//        authenticationManager.authenticate(authenticationToken);
//        return jwtTokenUtil.generateToken(existingUser);
//
//    }
@Override
public String login(
        String phoneNumber,
        String password,
        Long roleId
) throws Exception {
    Optional<User> optionalUser = userRepository.findByPhoneNumber(phoneNumber);
    if(optionalUser.isEmpty()) {
        throw new DataNotFoundException(localizationUtils.getLocalizedMessage(MessageKeys.WRONG_PHONE_PASSWORD));
    }
    //return optionalUser.get();//muốn trả JWT token ?
    User existingUser = optionalUser.get();
    //check password
    if (existingUser.getFacebookAccountId() == 0
            && existingUser.getGoogleAccountId() == 0) {
        if(!passwordEncoder.matches(password, existingUser.getPassword())) {
            throw new BadCredentialsException(localizationUtils.getLocalizedMessage(MessageKeys.WRONG_PHONE_PASSWORD));
        }
    }
    Optional<Role> optionalRole = roleRepository.findById(roleId);
    if(optionalRole.isEmpty() || !roleId.equals(existingUser.getRole().getId())) {
        throw new DataNotFoundException(localizationUtils.getLocalizedMessage(MessageKeys.ROLE_DOES_NOT_EXISTS));
    }
    if(!optionalUser.get().isActive()) {
        throw new DataNotFoundException(localizationUtils.getLocalizedMessage(MessageKeys.USER_IS_LOCKED));
    }
    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
            phoneNumber, password,
            existingUser.getAuthorities()
    );

    //authenticate with Java Spring security
    authenticationManager.authenticate(authenticationToken);
    return jwtTokenUtil.generateToken(existingUser);
}
    public void blockUser(Long userId) throws DataNotFoundException {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setIsActive(false);

            userRepository.save(user);
        } else {
            throw new DataNotFoundException("User not found");
        }
    }
}
