package com.researchspace.model.oauth;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.researchspace.model.User;

public class OAuthTokenTest {

	private User anyUser;

	@BeforeEach
	void before() {
		anyUser = createAnyUser("any");
	}

	@ParameterizedTest(name = "{index}: user:{0}, clientId:{1}, tokenType:{2})")
	@MethodSource("constructorValidationArguments")
	void constructorValidationNoNullArgs(User user, String clientId, OAuthTokenType tokenType) {
		assertIllegalArgumentException(() -> new OAuthToken(user, clientId, tokenType));
	}

	static Stream<Arguments> constructorValidationArguments() {
		return Stream.of(Arguments.of(null, "clientid", OAuthTokenType.UI_TOKEN),
				Arguments.of(createAnyUser("any"), "", OAuthTokenType.UI_TOKEN),
				Arguments.of(createAnyUser("any"), null, OAuthTokenType.UI_TOKEN),
				Arguments.of(createAnyUser("any"), "clientId", null),
				Arguments.of(null, null, null)
		);
	}

}
