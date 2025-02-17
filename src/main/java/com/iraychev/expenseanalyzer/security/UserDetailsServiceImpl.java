//package com.iraychev.expenseanalyzer.security;
//
//import com.iraychev.expenseanalyzer.domain.entity.User;
//import com.iraychev.expenseanalyzer.exception.ResourceNotFoundException;
//import com.iraychev.expenseanalyzer.repository.UserRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//public class UserDetailsServiceImpl implements UserDetailsService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        log.debug("Loading user by username {}", username);
//        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
//
//        List<GrantedAuthority> grantedAuthorities = user.getAuthorities().stream()
//                .map(Enum::name)
//                .map(SimpleGrantedAuthority::new)
//                .collect(Collectors.toList());
//
//        log.debug("User details loaded successfully for username: {}, Authorities: {}", username, grantedAuthorities);
//
//        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), grantedAuthorities);
//    }
//}
