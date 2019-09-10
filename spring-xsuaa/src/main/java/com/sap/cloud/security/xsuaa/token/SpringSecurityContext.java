package com.sap.cloud.security.xsuaa.token;

import com.sap.cloud.security.xsuaa.extractor.AuthoritiesExtractor;
import com.sap.cloud.security.xsuaa.token.authentication.XsuaaJwtDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.Assert;

import javax.swing.text.html.Option;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public class SpringSecurityContext {

	private static final Logger logger = LoggerFactory.getLogger(SpringSecurityContext.class);

	/**
	 * Obtain the Token object from the Spring Security Context
	 * {@link SecurityContextHolder}
	 *
	 * @return Token object
	 * @throws AccessDeniedException
	 *             in case there is no token, user is not authenticated
	 *             <p>
	 *             Note: This method is introduced with xsuaa spring client lib.
	 */
	static public Token getToken() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null) {
			throw new AccessDeniedException("Access forbidden: not authenticated");
		}

		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		Assert.state(principal != null, "Principal must not be null");
		Assert.state(principal instanceof Token, "Unexpected principal type");

		return (Token) principal;
	}

	/**
	 * Initializes the Spring Security Context {@link SecurityContextHolder} and
	 * extracts the authorities. With version 1.5.0 you can configure your own
	 * {@link AuthoritiesExtractor} to specify how to extract the authorities.
	 *
	 * @param encodedJwtToken
	 *            the jwt token that is decoded with the given JwtDecoder
	 * @param xsuaaJwtDecoder
	 *            the decoder of type {@link XsuaaJwtDecoder}
	 * @param authoritiesExtractor
	 *            the extractor used to turn Jwt scopes into Spring Security
	 *            authorities.
	 */
	static public void init(String encodedJwtToken, JwtDecoder xsuaaJwtDecoder,
			AuthoritiesExtractor authoritiesExtractor) {
		Assert.isInstanceOf(XsuaaJwtDecoder.class, xsuaaJwtDecoder,
				"Passed JwtDecoder instance must be of type 'XsuaaJwtDecoder'");
		Jwt jwtToken = xsuaaJwtDecoder.decode(encodedJwtToken);

		TokenAuthenticationConverter authenticationConverter = new TokenAuthenticationConverter(authoritiesExtractor);
		Authentication authentication = authenticationConverter.convert(jwtToken);

		SecurityContextHolder.createEmptyContext();
		SecurityContextHolder.getContext().setAuthentication(authentication);

		injectTokenIntoSecurityContext(jwtToken.getTokenValue());
	}

	/**
	 * Cleans up the Spring Security Context {@link SecurityContextHolder} and
	 * release thread locals for Garbage Collector to avoid memory leaks resources.
	 */
	static public void clear() {
		SecurityContextHolder.clearContext();
		getSecurityContextClass().ifPresent(securityContext -> {
			try {
				Method clear = securityContext.getDeclaredMethod("clear");
				clear.invoke(securityContext);
			} catch (NoSuchMethodException e) {
				logger.debug("Clear method not found on SecurityContext", e);
			} catch (IllegalAccessException | InvocationTargetException e) {
				logger.error("Could not invoke clear method on SecurityContext", e);
			}
		});
	}

	public static void injectTokenIntoSecurityContext(String tokenValue) {
		getSecurityContextClass().ifPresent(securityContext -> {
			try {
				Method init = securityContext.getDeclaredMethod("init", String.class);
				init.invoke(securityContext, tokenValue);
			} catch (NoSuchMethodException e) {
				logger.debug("Init method not found on SecurityContext", e);
			} catch (IllegalAccessException | InvocationTargetException e) {
				logger.error("Could not invoke init method on SecurityContext", e);
			}
		});
	}

	private static Optional<Class<?>> getSecurityContextClass() {
		try {
			return Optional.of(Class.forName("com.sap.xs2.security.container.SecurityContext"));
		} catch (ClassNotFoundException e) {
			logger.debug("SecurityContext class not found", e);
		}
		return Optional.empty();
	}
}