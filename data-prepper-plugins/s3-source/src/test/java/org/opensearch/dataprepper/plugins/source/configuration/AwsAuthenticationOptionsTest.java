/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AwsAuthenticationOptionsTest {

    private AwsAuthenticationOptions awsAuthenticationOptions;

    @BeforeEach
    void setUp() {
        awsAuthenticationOptions = new AwsAuthenticationOptions();
    }

    @Test
    void getAwsRegion_returns_Region_of() throws NoSuchFieldException, IllegalAccessException {
        final String regionString = UUID.randomUUID().toString();
        final Region expectedRegionObject = mock(Region.class);
        reflectivelySetField(awsAuthenticationOptions, "awsRegion", regionString);
        final Region actualRegion;
        try(final MockedStatic<Region> regionMockedStatic = mockStatic(Region.class)) {
            regionMockedStatic.when(() -> Region.of(regionString)).thenReturn(expectedRegionObject);
            actualRegion = awsAuthenticationOptions.getAwsRegion();
        }
        assertThat(actualRegion, equalTo(expectedRegionObject));
    }

    @Test
    void getAwsRegion_returns_null_when_region_is_null() throws NoSuchFieldException, IllegalAccessException {
        reflectivelySetField(awsAuthenticationOptions, "awsRegion", null);
        assertThat(awsAuthenticationOptions.getAwsRegion(), nullValue());
    }

    @Test
    void authenticateAWSConfiguration_should_return_s3Client_without_sts_role_arn() throws NoSuchFieldException, IllegalAccessException {
        reflectivelySetField(awsAuthenticationOptions, "awsRegion", "us-east-1");
        reflectivelySetField(awsAuthenticationOptions, "awsStsRoleArn", null);

        final DefaultCredentialsProvider mockedCredentialsProvider = mock(DefaultCredentialsProvider.class);
        final AwsCredentialsProvider actualCredentialsProvider;
        try (final MockedStatic<DefaultCredentialsProvider> defaultCredentialsProviderMockedStatic = mockStatic(DefaultCredentialsProvider.class)) {
            defaultCredentialsProviderMockedStatic.when(DefaultCredentialsProvider::create)
                    .thenReturn(mockedCredentialsProvider);
            actualCredentialsProvider = awsAuthenticationOptions.authenticateAwsConfiguration();
        }

        assertThat(actualCredentialsProvider, sameInstance(mockedCredentialsProvider));
    }

    @Nested
    class WithSts {
        private StsClient stsClient;
        private StsClientBuilder stsClientBuilder;

        @BeforeEach
        void setUp() {
            stsClient = mock(StsClient.class);
            stsClientBuilder = mock(StsClientBuilder.class);

            when(stsClientBuilder.build()).thenReturn(stsClient);
        }

        @Test
        void authenticateAWSConfiguration_should_return_s3Client_with_sts_role_arn() throws NoSuchFieldException, IllegalAccessException {
            reflectivelySetField(awsAuthenticationOptions, "awsRegion", "us-east-1");
            reflectivelySetField(awsAuthenticationOptions, "awsStsRoleArn", "arn:aws:iam::123456789012:iam-role");

            when(stsClientBuilder.region(Region.US_EAST_1)).thenReturn(stsClientBuilder);
            final AssumeRoleRequest.Builder assumeRoleRequestBuilder = mock(AssumeRoleRequest.Builder.class);
            when(assumeRoleRequestBuilder.roleSessionName(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.roleArn(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class);
                 final MockedStatic<AssumeRoleRequest> assumeRoleRequestMockedStatic = mockStatic(AssumeRoleRequest.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                assumeRoleRequestMockedStatic.when(AssumeRoleRequest::builder).thenReturn(assumeRoleRequestBuilder);
                actualCredentialsProvider = awsAuthenticationOptions.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));

            verify(assumeRoleRequestBuilder).roleArn("arn:aws:iam::123456789012:iam-role");
            verify(assumeRoleRequestBuilder).roleSessionName(anyString());
            verify(assumeRoleRequestBuilder).build();
            verifyNoMoreInteractions(assumeRoleRequestBuilder);
        }

        @Test
        void authenticateAWSConfiguration_should_return_s3Client_with_sts_role_arn_when_no_region() throws NoSuchFieldException, IllegalAccessException {
            reflectivelySetField(awsAuthenticationOptions, "awsRegion", null);
            reflectivelySetField(awsAuthenticationOptions, "awsStsRoleArn", "arn:aws:iam::123456789012:iam-role");
            assertThat(awsAuthenticationOptions.getAwsRegion(), equalTo(null));

            when(stsClientBuilder.region(null)).thenReturn(stsClientBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                actualCredentialsProvider = awsAuthenticationOptions.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));
        }

        @Test
        void authenticateAWSConfiguration_should_override_STS_Headers_when_HeaderOverrides_when_set() throws NoSuchFieldException, IllegalAccessException {
            final String headerName1 = UUID.randomUUID().toString();
            final String headerValue1 = UUID.randomUUID().toString();
            final String headerName2 = UUID.randomUUID().toString();
            final String headerValue2 = UUID.randomUUID().toString();
            final Map<String, String> overrideHeaders = Map.of(headerName1, headerValue1, headerName2, headerValue2);

            reflectivelySetField(awsAuthenticationOptions, "awsRegion", "us-east-1");
            reflectivelySetField(awsAuthenticationOptions, "awsStsRoleArn", "arn:aws:iam::123456789012:iam-role");
            reflectivelySetField(awsAuthenticationOptions, "awsStsHeaderOverrides", overrideHeaders);

            when(stsClientBuilder.region(Region.US_EAST_1)).thenReturn(stsClientBuilder);

            final AssumeRoleRequest.Builder assumeRoleRequestBuilder = mock(AssumeRoleRequest.Builder.class);
            when(assumeRoleRequestBuilder.roleSessionName(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.roleArn(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.overrideConfiguration(any(Consumer.class)))
                    .thenReturn(assumeRoleRequestBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class);
                 final MockedStatic<AssumeRoleRequest> assumeRoleRequestMockedStatic = mockStatic(AssumeRoleRequest.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                assumeRoleRequestMockedStatic.when(AssumeRoleRequest::builder).thenReturn(assumeRoleRequestBuilder);
                actualCredentialsProvider = awsAuthenticationOptions.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));

            final ArgumentCaptor<Consumer<AwsRequestOverrideConfiguration.Builder>> configurationCaptor = ArgumentCaptor.forClass(Consumer.class);

            verify(assumeRoleRequestBuilder).roleArn("arn:aws:iam::123456789012:iam-role");
            verify(assumeRoleRequestBuilder).roleSessionName(anyString());
            verify(assumeRoleRequestBuilder).overrideConfiguration(configurationCaptor.capture());
            verify(assumeRoleRequestBuilder).build();
            verifyNoMoreInteractions(assumeRoleRequestBuilder);

            final Consumer<AwsRequestOverrideConfiguration.Builder> actualOverride = configurationCaptor.getValue();

            final AwsRequestOverrideConfiguration.Builder configurationBuilder = mock(AwsRequestOverrideConfiguration.Builder.class);
            actualOverride.accept(configurationBuilder);
            verify(configurationBuilder).putHeader(headerName1, headerValue1);
            verify(configurationBuilder).putHeader(headerName2, headerValue2);
            verifyNoMoreInteractions(configurationBuilder);
        }

        @Test
        void authenticateAWSConfiguration_should_not_override_STS_Headers_when_HeaderOverrides_are_empty() throws NoSuchFieldException, IllegalAccessException {
            reflectivelySetField(awsAuthenticationOptions, "awsRegion", "us-east-1");
            reflectivelySetField(awsAuthenticationOptions, "awsStsRoleArn", "arn:aws:iam::123456789012:iam-role");
            reflectivelySetField(awsAuthenticationOptions, "awsStsHeaderOverrides", Collections.emptyMap());

            when(stsClientBuilder.region(Region.US_EAST_1)).thenReturn(stsClientBuilder);
            final AssumeRoleRequest.Builder assumeRoleRequestBuilder = mock(AssumeRoleRequest.Builder.class);
            when(assumeRoleRequestBuilder.roleSessionName(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.roleArn(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class);
                 final MockedStatic<AssumeRoleRequest> assumeRoleRequestMockedStatic = mockStatic(AssumeRoleRequest.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                assumeRoleRequestMockedStatic.when(AssumeRoleRequest::builder).thenReturn(assumeRoleRequestBuilder);
                actualCredentialsProvider = awsAuthenticationOptions.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));

            verify(assumeRoleRequestBuilder).roleArn("arn:aws:iam::123456789012:iam-role");
            verify(assumeRoleRequestBuilder).roleSessionName(anyString());
            verify(assumeRoleRequestBuilder).build();
            verifyNoMoreInteractions(assumeRoleRequestBuilder);
        }
    }

    private void reflectivelySetField(final AwsAuthenticationOptions awsAuthenticationOptions, final String fieldName, final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = AwsAuthenticationOptions.class.getDeclaredField(fieldName);
        try {
            field.setAccessible(true);
            field.set(awsAuthenticationOptions, value);
        } finally {
            field.setAccessible(false);
        }
    }
}