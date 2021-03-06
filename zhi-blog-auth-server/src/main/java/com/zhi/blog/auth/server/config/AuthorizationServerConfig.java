package com.zhi.blog.auth.server.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ClientSettings;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.config.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * @author Ted
 * @date 2022/6/22
 **/
@RequiredArgsConstructor
@EnableWebSecurity
public class AuthorizationServerConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * token ??????
     * @return
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            Principal principal = context.getPrincipal();
            context.getHeaders().header("dfyH", context.getRegisteredClient().getClientId() + "dfyH");
            context.getClaims().claim("dfyC", "dfyClaim");
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
                //?????????????????????
                .exceptionHandling(temp -> temp.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        //spring security ??????
        return httpSecurity
                .authorizeRequests().anyRequest().authenticated()
                .and()
                .formLogin(Customizer.withDefaults())
                .build();
    }

    /**
     * ?????????
     * @return WebSecurityCustomizer
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().antMatchers("/actuator/**", "/druid/**");
    }

    /**
     * ????????????????????????
     * @param jdbcTemplate
     * @return
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        //dodo 2022/6/24 registeredClientRepository ???????????????
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("default")
                .clientSecret(passwordEncoder().encode("Default@1024"))
                //clientAuthenticationMethod ???????????????????????????
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                //????????????????????????(clientId???clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                //???????????????
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://127.0.0.1:9014/zhi-blog-auth-server/authorized")
                .redirectUri("http://127.0.0.1:9011/zhi-blog-comment/login/oauth2/code/default")
                .redirectUri("https://www.baidu.com")
                //????????? ??????????????????openid??????/userinfo
                .scopes(strings -> strings.addAll(List.of("default", "openid")))
                //????????????
                .clientSettings(ClientSettings.builder()
                        //??????????????????????????????
                        .requireAuthorizationConsent(false).build())
                //????????????
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofDays(1))
                        .refreshTokenTimeToLive(Duration.ofDays(3))
                        .reuseRefreshTokens(true).build())
                .build();
        var  repository = new JdbcRegisteredClientRepository(jdbcTemplate);
        if (repository.findByClientId("default") == null) {
            repository.save(registeredClient);
        }
        return repository;
    }

    /**
     * ????????????????????????
     * @param jdbcTemplate
     * @param repository
     * @return
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository repository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, repository);
    }

    /**
     * ??????????????????
     * @param jdbcTemplate
     * @param repository
     * @return
     */
    @Bean
    public OAuth2AuthorizationConsentService auth2AuthorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                              RegisteredClientRepository repository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, repository);
    }

    @Bean
    public UserDetailsService userDetailService() {
        //dodo 2022/6/20 loadUserByUsername db
        return new InMemoryUserDetailsManager(User.builder()
                .username("user")
                .password(passwordEncoder().encode("123"))
                .authorities("default")
                .build()
        );
    }

    /**
     * ?????????????????????(????????????,???????????????)
     * @return
     */
    @Bean
    public ProviderSettings providerSettings() {
        //providerSettings ????????????????????????
        return ProviderSettings.builder()
                .issuer("http://dfy.com:9016/zhi-blog-auth-server")
                .build();
    }

    /**
     * keytool -genkeypair -alias ${alias} --keyalg RSA -keypass ${pass} -keystore ${alias}.jks -storepass ${pass}
     * keytool -list -rfc --keystore ${alias}.jks | openssl x509 -inform pem -pubkey
     * @return ??????rsa key
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        String alias = "zhi-blog";
        char[] pwd = "Nov2016".toCharArray();
        String fileName = "zhi-blog.jks";
        var keyStore = KeyStore.getInstance(new ClassPathResource(fileName).getFile(), pwd);
        var publicKey = ((RSAPublicKey) keyStore.getCertificate(alias).getPublicKey());
        var privateKey = ((PrivateKey) keyStore.getKey(alias, pwd));

        RSAKey rsaKey = new RSAKey
                .Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

}
