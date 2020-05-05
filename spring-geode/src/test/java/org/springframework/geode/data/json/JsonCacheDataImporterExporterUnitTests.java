/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.springframework.geode.data.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.cache.Region;
import org.apache.geode.pdx.PdxInstance;

import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.geode.data.json.converter.JsonToPdxConverter;
import org.springframework.geode.data.json.converter.ObjectToJsonConverter;

import example.app.crm.model.Customer;
import example.app.pos.model.LineItem;

/**
 * Unit Tests for {@link JsonCacheDataImporterExporter}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.pdx.PdxInstance
 * @see org.springframework.core.io.Resource
 * @see org.springframework.geode.data.json.JsonCacheDataImporterExporter
 * @since 1.3.0
 */
@RunWith(MockitoJUnitRunner.class)
public class JsonCacheDataImporterExporterUnitTests {

	@Spy
	private JsonCacheDataImporterExporter importer;

	@Test
	@SuppressWarnings("unchecked")
	public void doExportFromRegionLogsAndSavesJson() {

		String json = "[{ \"name\": \"Jon Doe\"}]";

		Resource mockResource = mock(Resource.class);

		Region<?, ?> mockRegion = mock(Region.class);

		doReturn("TestRegion").when(mockRegion).getName();

		doReturn(JsonCacheDataImporterExporter.FILESYSTEM_RESOURCE_PREFIX).when(importer).getResourceLocation();
		doReturn(Optional.of(mockResource)).when(this.importer).getResource(eq(mockRegion),
			eq(JsonCacheDataImporterExporter.FILESYSTEM_RESOURCE_PREFIX));
		doNothing().when(this.importer).save(anyString(), any(Resource.class));
		doReturn(json).when(this.importer).toJson(any());

		assertThat(this.importer.doExportFrom(mockRegion)).isEqualTo(mockRegion);

		verify(this.importer, times(1)).toJson(eq(mockRegion));
		verify(this.importer, times(1))
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.FILESYSTEM_RESOURCE_PREFIX));
		verify(this.importer, times(1)).save(eq(json), eq(mockResource));
	}

	@Test(expected = IllegalArgumentException.class)
	public void doExportFromNullRegion() {

		try {
			this.importer.doExportFrom(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Region must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void saveJsonToResource() throws IOException {

		String json = "[{ \"name\": \"Jon Doe\"}, { \"name\": \"Jane Doe\"}]";

		StringWriter writer = new StringWriter(json.length());

		Resource mockResource = mock(Resource.class);

		doReturn(writer).when(this.importer).newWriter(eq(mockResource));

		this.importer.save(json, mockResource);

		assertThat(writer.toString()).isEqualTo(json);

		verify(this.importer, times(1)).newWriter(eq(mockResource));
	}

	@Test(expected = DataAccessResourceFailureException.class)
	public void saveThrowsIoException() throws IOException {

		Writer mockWriter = mock(Writer.class);

		String json = "[{ \"name\": \"Jon Doe\"}, { \"name\": \"Jane Doe\"}"
			+ ", { \"name\": \"Pie Doe\"}, { \"name\": \"Sour Doe\"}]";

		Resource mockResource = mock(Resource.class);

		doReturn("/path/to/resource.json").when(mockResource).getDescription();
		doReturn(mockWriter).when(this.importer).newWriter(eq(mockResource));
		doThrow(new IOException("TEST")).when(mockWriter).write(anyString(), anyInt(), anyInt());

		try {
			this.importer.save(json, mockResource);
		}
		catch (DataAccessResourceFailureException expected) {

			assertThat(expected)
				.hasMessageStartingWith("Failed to save content '%s' to Resource [/path/to/resource.json]",
					this.importer.formatContentForPreview(json));
			assertThat(expected).hasCauseInstanceOf(IOException.class);
			assertThat(expected.getCause()).hasMessage("TEST");
			assertThat(expected.getCause()).hasNoCause();

			throw expected;
		}
		finally {
			verify(this.importer, times(1)).newWriter(eq(mockResource));
			verify(mockWriter, times(1)).write(eq(json), eq(0), eq(json.length()));
		}
	}
	@Test
	public void saveWithNoContent() throws IOException {

		Resource mockResource = mock(Resource.class);

		try {
			this.importer.save("  ", mockResource);
		}
		finally {
			verify(this.importer, never()).newWriter(any(Resource.class));
			verifyNoInteractions(mockResource);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void saveWithNullResource() {

		try {
			this.importer.save("{}", null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Resource must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doImportIntoPutsPdxIntoRegionForJson() {

		Resource mockResource = mock(Resource.class);

		Region<Integer, PdxInstance> mockRegion = mock(Region.class);

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		byte[] json = "{ \"name\": \"Jon Doe\"}".getBytes();

		doReturn(Optional.of(mockResource)).when(this.importer).getResource(eq(mockRegion),
			eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		doReturn(true).when(mockResource).exists();
		doReturn(json).when(this.importer).getContent(eq(mockResource));
		doReturn(mockPdxInstance).when(this.importer).toPdx(eq(json));
		doReturn(1).when(this.importer).getIdentifier(eq(mockPdxInstance));

		assertThat(this.importer.doImportInto(mockRegion)).isEqualTo(mockRegion);

		verify(this.importer, times(1))
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		verify(this.importer, times(1)).getContent(eq(mockResource));
		verify(this.importer, times(1)).toPdx(eq(json));
		verify(this.importer, times(1)).getIdentifier(eq(mockPdxInstance));
		verify(mockResource, times(1)).exists();
		verify(mockRegion, times(1)).put(eq(1), eq(mockPdxInstance));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doImportIntoWithNoResource() {

		Region<?, ?> mockRegion = mock(Region.class);

		doReturn(Optional.empty()).when(this.importer)
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));

		assertThat(this.importer.doImportInto(mockRegion)).isEqualTo(mockRegion);

		verify(this.importer, times(1)).doImportInto(eq(mockRegion));
		verify(this.importer, times(1))
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		verifyNoMoreInteractions(this.importer);
		verifyNoInteractions(mockRegion);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doImportIntoWithNonExistingResource() {

		Resource mockResource = mock(Resource.class);

		Region<?, ?> mockRegion = mock(Region.class);

		doReturn(Optional.of(mockResource)).when(this.importer)
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		doReturn(false).when(mockResource).exists();

		assertThat(this.importer.doImportInto(mockRegion)).isEqualTo(mockRegion);

		verify(this.importer, times(1)).doImportInto(eq(mockRegion));
		verify(this.importer, times(1))
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		verify(mockResource, times(1)).exists();
		verifyNoMoreInteractions(this.importer);
		verifyNoMoreInteractions(mockResource);
		verifyNoInteractions(mockRegion);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doImportIntoWithResourceContainingNoContent() {

		Resource mockResource = mock(Resource.class);

		Region<?, ?> mockRegion = mock(Region.class);

		doReturn(Optional.of(mockResource)).when(this.importer)
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		doReturn(true).when(mockResource).exists();
		doReturn(null).when(this.importer).getContent(eq(mockResource));

		assertThat(this.importer.doImportInto(mockRegion)).isEqualTo(mockRegion);

		verify(this.importer, times(1)).doImportInto(eq(mockRegion));
		verify(this.importer, times(1))
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		verify(this.importer, times(1)).getContent(eq(mockResource));
		verify(mockResource, times(1)).exists();
		verifyNoMoreInteractions(this.importer);
		verifyNoMoreInteractions(mockResource);
		verifyNoInteractions(mockRegion);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doImportIntoWithNoPdxInstance() {

		Resource mockResource = mock(Resource.class);

		Region<?, ?> mockRegion = mock(Region.class);

		byte[] json = "[]".getBytes();

		doReturn(Optional.of(mockResource)).when(this.importer)
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		doReturn(true).when(mockResource).exists();
		doReturn(json).when(this.importer).getContent(eq(mockResource));
		doReturn(null).when(this.importer).toPdx(eq(json));

		assertThat(this.importer.doImportInto(mockRegion)).isEqualTo(mockRegion);

		verify(this.importer, times(1)).doImportInto(eq(mockRegion));
		verify(this.importer, times(1))
			.getResource(eq(mockRegion), eq(JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX));
		verify(this.importer, times(1)).getContent(eq(mockResource));
		verify(this.importer, times(1)).toPdx(eq(json));
		verify(mockResource, times(1)).exists();
		verifyNoMoreInteractions(this.importer);
		verifyNoMoreInteractions(mockResource);
		verifyNoInteractions(mockRegion);
	}

	@Test(expected = IllegalArgumentException.class)
	public void doImportIntoNullRegion() {

		try {
			this.importer.doImportInto(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Region must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void getContentFromResource() throws IOException {

		byte[] json = "{ \"name\": \"Jon Doe\" }".getBytes();

		ByteArrayInputStream in = spy(new ByteArrayInputStream(json));

		Resource mockResource = mock(Resource.class);

		doReturn(in).when(mockResource).getInputStream();

		assertThat(this.importer.getContent(mockResource)).isEqualTo(json);

		verify(mockResource, times(1)).getInputStream();
		verify(in, times(1)).close();
	}

	@Test(expected = IllegalArgumentException.class)
	public void getContentFromNullResource() {

		try {
			this.importer.getContent(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Resource must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = DataAccessResourceFailureException.class)
	public void getContentThrowsIoException() throws IOException {

		Resource mockResource = mock(Resource.class);

		doReturn("Test Resource").when(mockResource).getDescription();
		doThrow(new IOException("TEST")).when(mockResource).getInputStream();

		try {
			this.importer.getContent(mockResource);
		}
		catch (DataAccessResourceFailureException expected) {

			assertThat(expected).hasMessageStartingWith("Failed to read from Resource [Test Resource]");
			assertThat(expected).hasCauseInstanceOf(IOException.class);
			assertThat(expected.getCause()).hasMessage("TEST");
			assertThat(expected.getCause()).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void getIdentifierFromPdxInstance() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(Arrays.asList("age", "id", "name")).when(mockPdxInstance).getFieldNames();
		doReturn(true).when(mockPdxInstance).isIdentityField(eq("id"));
		doReturn(42).when(mockPdxInstance).getField(eq("id"));

		assertThat(this.importer.getIdentifier(mockPdxInstance)).isEqualTo(42);

		verify(this.importer, never()).getIdField(any(PdxInstance.class));
		verify(mockPdxInstance, times(1)).getFieldNames();
		verify(mockPdxInstance, times(1)).isIdentityField(eq("age"));
		verify(mockPdxInstance, times(1)).isIdentityField(eq("id"));
		verify(mockPdxInstance, never()).isIdentityField(eq("name"));
		verify(mockPdxInstance, times(1)).getField(eq("id"));
	}

	@Test
	public void getIdentifierFromPdxInstanceHavingNoFields() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(null).when(mockPdxInstance).getFieldNames();
		doReturn(69).when(this.importer).getIdField(eq(mockPdxInstance));

		assertThat(this.importer.getIdentifier(mockPdxInstance)).isEqualTo(69);

		verify(this.importer, times(1)).getIdField(eq(mockPdxInstance));
		verify(mockPdxInstance, times(1)).getFieldNames();
		verify(mockPdxInstance, never()).isIdentityField(anyString());
		verify(mockPdxInstance, never()).getField(anyString());
	}

	@Test
	public void getIdentifierFromPdxInstanceHavingNoIdentityFields() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(Arrays.asList("", "age", null, "name", "  ")).when(mockPdxInstance).getFieldNames();
		doReturn(false).when(mockPdxInstance).isIdentityField(anyString());
		doReturn(101).when(this.importer).getIdField(eq(mockPdxInstance));

		assertThat(this.importer.getIdentifier(mockPdxInstance)).isEqualTo(101);

		verify(this.importer, times(1)).getIdField(eq(mockPdxInstance));
		verify(mockPdxInstance, times(1)).getFieldNames();
		verify(mockPdxInstance, times(1)).isIdentityField(eq("age"));
		verify(mockPdxInstance, times(1)).isIdentityField(eq("name"));
		verify(mockPdxInstance, never()).isIdentityField(isNull());
		verify(mockPdxInstance, never()).isIdentityField(eq(""));
		verify(mockPdxInstance, never()).isIdentityField(eq("  "));
		verify(mockPdxInstance, never()).getField(anyString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void getIdentifierFromNullPdxInstance() {

		try {
			this.importer.getIdentifier(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("PdxInstance must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalStateException.class)
	public void getIdentifierFromPdxInstanceWithNoIdentifier() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(Collections.singletonList("name")).when(mockPdxInstance).getFieldNames();
		doReturn(false).when(mockPdxInstance).isIdentityField(anyString());
		doThrow(new IllegalStateException("TEST")).when(this.importer).getIdField(eq(mockPdxInstance));

		try {
			this.importer.getIdentifier(mockPdxInstance);
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("TEST");
			assertThat(expected).hasNoCause();

			throw expected;
		}
		finally {
			verify(mockPdxInstance, times(1)).getFieldNames();
			verify(mockPdxInstance, times(1)).isIdentityField(eq("name"));
			verify(mockPdxInstance, never()).getField(anyString());
		}
	}

	@Test
	public void getIdFieldFromPdxInstance() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(true).when(mockPdxInstance).hasField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
		doReturn(42).when(mockPdxInstance).getField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));

		assertThat(this.importer.getIdField(mockPdxInstance)).isEqualTo(42);

		verify(mockPdxInstance, times(1))
			.hasField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
		verify(mockPdxInstance, times(1))
			.getField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
	}

	@Test(expected = IllegalArgumentException.class)
	public void getIdFieldFromNullPdxInstance() {

		try {
			this.importer.getIdField(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("PdxInstance must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalStateException.class)
	public void getIdFieldFromPdxInstanceWithNoIdField() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(LineItem.class.getName()).when(mockPdxInstance).getClassName();
		doReturn(false).when(mockPdxInstance).hasField(anyString());

		try {
			this.importer.getIdField(mockPdxInstance);
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("PdxInstance for type [%s] has no declared identity field",
				LineItem.class.getName());
			assertThat(expected).hasNoCause();

			throw expected;
		}
		finally {
			verify(mockPdxInstance, times(1)).getClassName();
			verify(mockPdxInstance, times(2))
				.hasField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
			verify(mockPdxInstance, never()).getField(anyString());
		}
	}

	@Test(expected = IllegalStateException.class)
	public void getIdFieldFromPdxInstanceWithNoId() {

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(true).when(mockPdxInstance).hasField(anyString());
		doReturn(null).when(mockPdxInstance).getField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
		doReturn(LineItem.class.getName()).when(mockPdxInstance).getClassName();

		try {
			this.importer.getIdField(mockPdxInstance);
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("PdxInstance for type [%s] has no id",
				LineItem.class.getName());
			assertThat(expected).hasNoCause();

			throw expected;
		}
		finally {
			verify(mockPdxInstance, times(1)).getClassName();
			verify(mockPdxInstance, times(1))
				.getField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
			verify(mockPdxInstance, times(2))
				.hasField(eq(JsonCacheDataImporterExporter.ID_FIELD_NAME));
		}
	}

	@Test
	public void getResourceFromRegionAndResourcePrefix() {

		ApplicationContext mockApplicationContext = mock(ApplicationContext.class);

		Region<?, ?> mockRegion = mock(Region.class);

		Resource mockResource = mock(Resource.class);

		doReturn(mockResource).when(mockApplicationContext).getResource(anyString());
		doReturn("Example").when(mockRegion).getName();
		doReturn(Optional.of(mockApplicationContext)).when(this.importer).getApplicationContext();

		assertThat(this.importer.getResource(mockRegion, "sftp://host/resources/").orElse(null))
			.isEqualTo(mockResource);

		verify(mockApplicationContext, times(1))
			.getResource(eq("sftp://host/resources/data-example.json"));
		verify(mockRegion, times(1)).getName();
	}

	@Test
	public void getResourceWhenApplicationContextIsNotPresent() {

		Region<?, ?> mockRegion = mock(Region.class);

		doReturn("TEST").when(mockRegion).getName();

		Resource resource = this.importer.getResource(mockRegion, "file://").orElse(null);

		assertThat(resource).isInstanceOf(ClassPathResource.class);
		assertThat(((ClassPathResource) resource).getPath()).isEqualTo("data-test.json");

		verify(mockRegion, times(1)).getName();
	}

	@Test
	public void getResourceWhenApplicationContextReturnsNoResource() {

		ApplicationContext mockApplicationContext = mock(ApplicationContext.class);

		Region<?, ?> mockRegion = mock(Region.class);

		doReturn(null).when(mockApplicationContext).getResource(anyString());
		doReturn("MocK").when(mockRegion).getName();
		doReturn(Optional.ofNullable(mockApplicationContext)).when(this.importer).getApplicationContext();

		Resource resource = this.importer.getResource(mockRegion, null).orElse(null);

		assertThat(resource).isInstanceOf(ClassPathResource.class);
		assertThat(((ClassPathResource) resource).getPath()).isEqualTo("data-mock.json");

		verify(mockApplicationContext, times(1))
			.getResource(eq("classpath:data-mock.json"));
		verify(mockRegion, times(1)).getName();
	}

	@Test(expected = IllegalArgumentException.class)
	public void getResourceFromNullRegion() {

		try {
			this.importer.getResource(null, JsonCacheDataImporterExporter.CLASSPATH_RESOURCE_PREFIX);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Region must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void getResourceLocationIsInWorkingDirectory() {
		assertThat(this.importer.getResourceLocation()).isEqualTo(String.format("%1$s%2$s",
			JsonCacheDataImporterExporter.FILESYSTEM_RESOURCE_PREFIX, System.getProperty("user.dir")));
	}

	@Test
	public void toJsonFromRegion() {

		String jonDoeJson = "{ \"name\": \"Jon Doe\" }";
		String janeDoeJson = "{ \"name\": \"Jane Doe\" }";
		String pieDoeJson = "{ \"name\": \"Pie Doe\" }";
		String expectedJson = String.format("[%1$s, %2$s, %3$s]", jonDoeJson, janeDoeJson, pieDoeJson);

		Customer jonDoe = Customer.newCustomer(1L, "Jon Doe");
		Customer janeDoe = Customer.newCustomer(2L, "Jane Doe");
		Customer pieDoe = Customer.newCustomer(3L, "Pie Doe");

		Region<?, ?> customers = mock(Region.class);

		doReturn(Arrays.asList(jonDoe, janeDoe, pieDoe)).when(customers).values();
		doReturn(jonDoeJson).when(this.importer).toJson(eq(jonDoe));
		doReturn(janeDoeJson).when(this.importer).toJson(eq(janeDoe));
		doReturn(pieDoeJson).when(this.importer).toJson(eq(pieDoe));

		assertThat(this.importer.toJson(customers)).isEqualTo(expectedJson);

		verify(this.importer, times(1)).toJson(eq(jonDoe));
		verify(this.importer, times(1)).toJson(eq(janeDoe));
		verify(this.importer, times(1)).toJson(eq(pieDoe));
		verify(customers, times(1)).values();
	}

	@Test
	public void toJsonFromEmptyRegion() {

		Region<?, ?> mockRegion = mock(Region.class);

		assertThat(this.importer.toJson(mockRegion)).isEqualTo("[]");

		verify(mockRegion, times(1)).values();
	}

	@Test(expected = IllegalArgumentException.class)
	public void toJsonFromNullRegion() {

		try {
			this.importer.toJson(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Region must not be null");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void toJsonFromObjectCallsObjectToJsonConverter() {

		String json = "{ \"name\": \"Jon Doe\" }";

		ObjectToJsonConverter mockConverter = mock(ObjectToJsonConverter.class);

		doReturn(mockConverter).when(this.importer).getObjectToJsonConverter();
		doReturn(json).when(mockConverter).convert(eq("TEST"));

		assertThat(this.importer.toJson("TEST")).isEqualTo(json);

		verify(this.importer, times(1)).getObjectToJsonConverter();
		verify(mockConverter, times(1)).convert(eq("TEST"));
	}

	@Test
	public void toPdxFromJsonCallsJsonToPdxConverter() {

		byte[] json = "{ \"name\": \"Jon Doe\" }".getBytes();

		JsonToPdxConverter mockConverter = mock(JsonToPdxConverter.class);

		PdxInstance mockPdxInstance = mock(PdxInstance.class);

		doReturn(mockConverter).when(this.importer).getJsonToPdxConverter();
		doReturn(mockPdxInstance).when(mockConverter).convert(eq(json));

		assertThat(this.importer.toPdx(json)).isEqualTo(mockPdxInstance);

		verify(this.importer, times(1)).getJsonToPdxConverter();
		verify(mockConverter, times(1)).convert(eq(json));
	}
}