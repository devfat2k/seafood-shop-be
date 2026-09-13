package com.devfat.mini_ecommerce.shared.security;



import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


/*
 * JwtAuthenticationFilter nằm trong Security Filter Chain và kế thừa OncePerRequestFilter.
 *
 * Flow:
 * 1. Request từ Client đi qua Security Filter Chain.
 * 2. OncePerRequestFilter.doFilter() của class cha chạy trước,
 *    kiểm tra request này đã đi qua filter chưa.
 *    Nếu chưa -> gọi doFilterInternal() của JwtAuthenticationFilter.
 *    Nếu rồi -> bỏ qua để tránh chạy JWT logic nhiều lần trong cùng một request.
 *
 * 3. doFilterInternal() thực hiện nhiệm vụ:
 *    - Đọc JWT từ Authorization header.
 *    - Validate token.
 *    - Lấy thông tin user từ token.
 *    - Load UserDetails từ database.
 *    - Tạo Authentication và lưu vào SecurityContextHolder
 *      để Spring Security biết request hiện tại đã được xác thực.
 *
 * 4. Sau khi xử lý xong, gọi filterChain.doFilter(request, response)
 *    để chuyển request sang Filter tiếp theo trong Security Filter Chain.
 *
 * filterChain ở đây đại diện cho phần còn lại của chuỗi Filter phía sau JwtAuthenticationFilter.
 * JwtFilter chỉ xác thực và gắn thông tin user, không quyết định cho phép/chặn request.
 * Việc authorize (authenticated, role...) do các filter khác của Spring Security xử lý.
 */

@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        // Bước 1: đọc header "Authorization", tách lấy phần token sau "Bearer "
        // Bước 2: nếu có token VÀ jwtProvider.validateToken(token) == true:
        //     - lấy email từ token (jwtProvider.getEmailFromToken)
        //     - tra lại UserDetails qua userDetailsService.loadUserByUsername(email)
        //     - tạo UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        //     - set vào SecurityContextHolder.getContext().setAuthentication(...)
        // Bước 3: LUÔN gọi filterChain.doFilter(request, response) ở cuối — bất kể có token hay không
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
           String token =  header.substring(7);
           if (jwtProvider.validateToken(token)) {
               String email = jwtProvider.getEmailFromToken(token);
               UserDetails userDetails = userDetailsService.loadUserByUsername(email);

               if (userDetails.isEnabled()) {
                   UsernamePasswordAuthenticationToken authentication =
                           new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                   SecurityContextHolder.getContext().setAuthentication(authentication);
               } else {
                   log.warn("User account is disabled: {}", email);
               }
           }
        }
        filterChain.doFilter(request, response);
    }
}
