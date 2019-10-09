package com.sap.cloud.security.xsuaa.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static com.sap.cloud.security.xsuaa.client.OAuth2TokenServiceConstants.*;

import static com.sap.cloud.security.xsuaa.Assertions.assertNotNull;
import static com.sap.cloud.security.xsuaa.Assertions.assertHasText;

public class XsuaaOAuth2TokenService implements OAuth2TokenService {

	private final RestOperations restOperations;
	private static Logger logger = LoggerFactory.getLogger(XsuaaOAuth2TokenService.class);

	public XsuaaOAuth2TokenService(RestOperations restOperations) {
		Assert.notNull(restOperations, "restOperations is required");
		this.restOperations = restOperations;
	}

	@Override
	public OAuth2TokenResponse retrieveAccessTokenViaClientCredentialsGrant(URI tokenEndpointUri,
			ClientCredentials clientCredentials,
			@Nullable String subdomain, @Nullable Map<String, String> optionalParameters)
			throws OAuth2ServiceException {
		assertNotNull(tokenEndpointUri, "tokenEndpointUri is required");
		assertNotNull(clientCredentials, "clientCredentials is required");

		// build parameters
		Map<String, String> parameters = new HashMap<>();
		parameters.put(GRANT_TYPE, GRANT_TYPE_CLIENT_CREDENTIALS);
		addClientCredentialsToParameters(clientCredentials, parameters);
		if (optionalParameters != null) {
			optionalParameters.forEach(parameters::putIfAbsent);
		}

		// build header
		HttpHeaders headers = createHeadersWithoutAuthorization();

		return requestAccessToken(replaceSubdomain(tokenEndpointUri, subdomain), headers, copyIntoForm(parameters));
	}

	@Override
	public OAuth2TokenResponse retrieveAccessTokenViaUserTokenGrant(URI tokenEndpointUri,
			ClientCredentials clientCredentials, String token, @Nullable String subdomain,
			@Nullable Map<String, String> optionalParameters)
			throws OAuth2ServiceException {
		assertNotNull(tokenEndpointUri, "tokenEndpointUri is required");
		assertNotNull(clientCredentials, "clientCredentials is required");
		assertHasText(token, "token is required");

		// build parameters
		Map<String, String> parameters = new HashMap<>();
		parameters.put(GRANT_TYPE, GRANT_TYPE_USER_TOKEN);
		parameters.put(PARAMETER_CLIENT_ID, clientCredentials.getId());
		if (optionalParameters != null) {
			optionalParameters.forEach(parameters::putIfAbsent);
		}

		// build header
		HttpHeaders headers = createHeadersWithAuthorization(token);

		return requestAccessToken(replaceSubdomain(tokenEndpointUri, subdomain), headers, copyIntoForm(parameters));
	}

	@Override
	public OAuth2TokenResponse retrieveAccessTokenViaRefreshToken(URI tokenEndpointUri,
			ClientCredentials clientCredentials,
			String refreshToken, String subdomain) throws OAuth2ServiceException {
		assertNotNull(tokenEndpointUri, "tokenEndpointUri is required");
		assertNotNull(clientCredentials, "clientCredentials is required");
		assertHasText(refreshToken, "refreshToken is required");

		// build parameters
		Map<String, String> parameters = new HashMap<>();
		parameters.put(GRANT_TYPE, GRANT_TYPE_REFRESH_TOKEN);
		parameters.put(REFRESH_TOKEN, refreshToken);
		addClientCredentialsToParameters(clientCredentials, parameters);

		// build header
		HttpHeaders headers = createHeadersWithoutAuthorization();

		return requestAccessToken(replaceSubdomain(tokenEndpointUri, subdomain), headers, copyIntoForm(parameters));
	}

	@Override
	public OAuth2TokenResponse retrieveAccessTokenViaPasswordGrant(URI tokenEndpoint,
			ClientCredentials clientCredentials, String username, String password,
			@Nullable String subdomain, @Nullable Map<String, String> optionalParameters)
			throws OAuth2ServiceException {
		assertNotNull(tokenEndpoint, "tokenEndpoint is required");
		assertNotNull(clientCredentials, "clientCredentials are required");
		assertHasText(username, "username is required");
		assertHasText(password, "password is required");

		Map<String, String> parameters = new HashMap<>();
		parameters.put(GRANT_TYPE, GRANT_TYPE_PASSWORD);
		parameters.put(USERNAME, username);
		parameters.put(PASSWORD, password);
		addClientCredentialsToParameters(clientCredentials, parameters);

		if (optionalParameters != null) {
			optionalParameters.forEach(parameters::putIfAbsent);
		}

		HttpHeaders headers = createHeadersWithoutAuthorization();

		return requestAccessToken(replaceSubdomain(tokenEndpoint, subdomain), headers, copyIntoForm(parameters));
	}

	/**
	 * TODO currently fails with 400 (Bad Request)
	 * @param tokenEndpointUri
	 * @param clientCredentials contains id of master (extracted from VCAP_SERVICES system environment variable)
	 * @param oidcToken
	 * @param pemEncodedCloneCertificate
	 * @param subdomain
	 * @param optionalParameters
	 * @return
	 * @throws OAuth2ServiceException
	 */
	@Nullable
	public OAuth2TokenResponse retrieveDelegationAccessTokenViaJwtBearerTokenGrant(URI tokenEndpointUri,
			ClientCredentials clientCredentials, String oidcToken, String pemEncodedCloneCertificate, @Nullable String subdomain,
			@Nullable Map<String, String> optionalParameters) throws OAuth2ServiceException {
		assertNotNull(tokenEndpointUri, "tokenEndpoint is required");
		assertNotNull(clientCredentials.getId(), "client ID is required (master)");
		assertHasText(oidcToken, "oidcToken is required");
		assertHasText(pemEncodedCloneCertificate, "pemEncodedCertificate is required"); // w/o BEGIN CERTIFICATE ...

		if(!testCertificate()) {
			return null;
		}
		Map<String, String> parameters = new HashMap<>();

		//parameters.put(GRANT_TYPE, GRANT_TYPE_JWT_BEARER); // default client_x509
		parameters.put("master_client_id", clientCredentials.getId());
		parameters.put("clone_certificate", pemEncodedCloneCertificate);

		if (optionalParameters != null) {
			optionalParameters.forEach(parameters::putIfAbsent);
		}

		//HttpHeaders headers = createHeadersWithAuthorization(oidcToken);
		HttpHeaders headers = createHeadersWithoutAuthorization();
		return requestAccessToken(replaceSubdomain(tokenEndpointUri, subdomain), headers, copyIntoForm(parameters));
	}

	/**
	 * Utility method that replaces the subdomain of the URI with the given
	 * subdomain.
	 *
	 * @param uri
	 *            the URI to be replaced.
	 * @param subdomain
	 *            of the tenant.
	 * @return the URI with the replaced subdomain or the passed URI in case a
	 *         replacement was not possible.
	 */
	static URI replaceSubdomain(URI uri, @Nullable String subdomain) {
		Assert.notNull(uri, "the uri parameter must not be null");
		if (StringUtils.hasText(subdomain) && uri.getHost().contains(".")) {
			UriBuilder builder = UriComponentsBuilder.newInstance().scheme(uri.getScheme())
					.host(subdomain + uri.getHost().substring(uri.getHost().indexOf('.'))).port(uri.getPort())
					.path(uri.getPath());
			return uri.resolve(builder.build());
		}
		logger.warn("the subdomain of the URI '{}' is not replaced by subdomain '{}'", uri, subdomain);
		return uri;
	}

	private OAuth2TokenResponse requestAccessToken(URI tokenEndpointUri, HttpHeaders headers,
			MultiValueMap<String, String> parameters) throws OAuth2ServiceException {

		// Create URI
		UriComponentsBuilder builder = UriComponentsBuilder.fromUri(tokenEndpointUri);
		URI requestUri = builder.build().encode().toUri();

		// Create entity
		HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(parameters, headers);

		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> responseEntity = null;
		try {
			responseEntity = restOperations.postForEntity(requestUri, requestEntity, Map.class);
		} catch (HttpClientErrorException ex) {
			String warningMsg = String.format(
					"Error retrieving JWT token. Received status code %s. Call to XSUAA was not successful: %s",
					ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new OAuth2ServiceException(warningMsg);
		} catch (HttpServerErrorException ex) {
			String warningMsg = String.format("Server error while obtaining access token from XSUAA (%s): %s",
					ex.getStatusCode(), ex.getResponseBodyAsString());
			logger.error(warningMsg, ex);
			throw new OAuth2ServiceException(warningMsg);
		}

		@SuppressWarnings("unchecked")
		Map<String, String> accessTokenMap = responseEntity.getBody();
		logger.debug("Request Access Token: {}", responseEntity.getBody());

		String accessToken = accessTokenMap.get(ACCESS_TOKEN);
		long expiresIn = Long.parseLong(String.valueOf(accessTokenMap.get(EXPIRES_IN)));
		String refreshToken = accessTokenMap.get(REFRESH_TOKEN);
		return new OAuth2TokenResponse(accessToken, expiresIn, refreshToken);
	}

	private boolean testCertificate()
			throws OAuth2ServiceException {
		// TODO is it possible to check whether restOperation has SSLContext
		// TODO "authentication" domain -> "authentication.cert"
		// TODO delegation
		URI uri = URI.create("https://d047491-show-headers.cert.cfapps.sap.hana.ondemand.com");
		ResponseEntity<String> payload = restOperations.getForEntity(uri, String.class);

		return payload.getBody().contains("x-forwarded-client-cert");
	}

	/**
	 * Creates a copy of the given map or an new empty map of type MultiValueMap.
	 *
	 * @return a new @link{MultiValueMap} that contains all entries of the optional
	 *         map.
	 */
	private static MultiValueMap<String, String> copyIntoForm(Map<String, String> parameters) {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap();
		if (parameters != null) {
			parameters.forEach(formData::add);
		}
		return formData;
	}

	/**
	 * Creates the set of HTTP headers with client-credentials basic authentication
	 * header.
	 *
	 * @return the HTTP headers.
	 */
	private static HttpHeaders createHeadersWithoutAuthorization() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		return headers;
	}

	/**
	 * Creates the set of HTTP headers with Authorization Bearer header.
	 *
	 * @return the HTTP headers.
	 */
	private static HttpHeaders createHeadersWithAuthorization(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		addAuthorizationBearerHeader(headers, token);
		return headers;
	}

	private void addClientCredentialsToParameters(ClientCredentials clientCredentials,
			Map<String, String> parameters) {
		parameters.put(CLIENT_ID, clientCredentials.getId());
		parameters.put(CLIENT_SECRET, clientCredentials.getSecret());
	}

	/** common utilities **/

	/**
	 * Adds the {@code  Authorization: Bearer <token>} header to the set of headers.
	 *
	 * @param headers
	 *            - the set of headers to add the header to.
	 * @param token
	 *            - the token which should be part of the header.
	 */
	static void addAuthorizationBearerHeader(HttpHeaders headers, String token) {
		final String AUTHORIZATION_BEARER_TOKEN_FORMAT = "Bearer %s";
		headers.add(HttpHeaders.AUTHORIZATION, String.format(AUTHORIZATION_BEARER_TOKEN_FORMAT, token));
	}

}
