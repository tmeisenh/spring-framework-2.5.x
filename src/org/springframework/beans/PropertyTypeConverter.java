/*
 * Copyright 2002-2006 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.GenericsHelper;
import org.springframework.core.JdkVersion;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;

/**
 * Helper class for converting to property target types.
 *
 * <p>Works on a PropertyEditorRegistrySupport. Used by BeanWrapperImpl.
 *
 * @author Juergen Hoeller
 * @since 2.0
 */
class PropertyTypeConverter {

	/**
	 * We'll create a lot of these objects, so we don't want a new logger every time.
	 */
	private static final Log logger = LogFactory.getLog(PropertyTypeConverter.class);

	private final PropertyEditorRegistrySupport propertyEditorRegistry;


	public PropertyTypeConverter(PropertyEditorRegistrySupport propertyEditorRegistry) {
		this.propertyEditorRegistry = propertyEditorRegistry;
	}


	/**
	 * Convert the value to the required type (if necessary from a String),
	 * for the specified property.
	 * @param propertyName name of the property
	 * @param oldValue previous value, if available (may be <code>null</code>)
	 * @param newValue proposed change value
	 * @param requiredType the type we must convert to
	 * (or <code>null</code> if not known, for example in case of a collection element)
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	public Object doTypeConversionIfNecessary(
			String propertyName, Object oldValue, Object newValue, Class requiredType)
			throws IllegalArgumentException {

		return doTypeConversionIfNecessary(propertyName, oldValue, newValue, requiredType, null);
	}

	/**
	 * Convert the value to the required type (if necessary from a String),
	 * for the specified property.
	 * @param propertyName name of the property
	 * @param oldValue previous value, if available (may be <code>null</code>)
	 * @param newValue proposed change value
	 * @param requiredType the type we must convert to
	 * (or <code>null</code> if not known, for example in case of a collection element)
	 * @param methodParam the method parameter that is the target of the conversion
	 * (may be <code>null</code>)
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	public Object doTypeConversionIfNecessary(
			String propertyName, Object oldValue, Object newValue, Class requiredType, MethodParameter methodParam)
			throws IllegalArgumentException {

		Object convertedValue = newValue;

		// Custom editor for this type?
		PropertyEditor pe = this.propertyEditorRegistry.findCustomEditor(requiredType, propertyName);

		// Value not of required type?
		if (pe != null ||
				(requiredType != null && !requiredType.isInstance(convertedValue))) {

			if (requiredType != null) {
				if (pe == null) {
					// No custom editor -> check BeanWrapperImpl's default editors.
					pe = (PropertyEditor) this.propertyEditorRegistry.getDefaultEditor(requiredType);
					if (pe == null) {
						// No BeanWrapper default editor -> check standard JavaBean editors.
						pe = PropertyEditorManager.findEditor(requiredType);
					}
				}
			}

			convertedValue = convertValue(convertedValue, requiredType, pe, oldValue);
		}

		if (requiredType != null) {
			// Array required -> apply appropriate conversion of elements.
			if (requiredType.isArray()) {
				return convertToTypedArray(convertedValue, propertyName, requiredType.getComponentType());
			}

			if (methodParam != null) {
				if (convertedValue instanceof Collection) {
					convertedValue = convertToTypedCollection((Collection) convertedValue, propertyName, methodParam);
				}
				else if (convertedValue instanceof Map) {
					convertedValue = convertToTypedMap((Map) convertedValue, propertyName, methodParam);
				}
			}

			// If the resulting value definitely doesn't match the required type,
			// try field lookup as fallback. If no matching field found,
			// throw explicit TypeMismatchException with full context information.
			if (convertedValue != null && !requiredType.isPrimitive() && !requiredType.isInstance(convertedValue)) {

				// In case of String value, try to find matching field (for JDK 1.5
				// enum or custom enum with values defined as static fields).
				if (convertedValue instanceof String) {
					try {
						Field enumField = requiredType.getField((String) convertedValue);
						return enumField.get(null);
					}
					catch (Exception ex) {
						if (logger.isDebugEnabled()) {
							logger.debug("Field [" + convertedValue + "] isn't an enum value", ex);
						}
					}
				}

				// Definitely doesn't match: throw IllegalArgumentException.
				throw new IllegalArgumentException("No matching editors or conversion strategy found");
			}
		}

		return convertedValue;
	}

	protected Object convertValue(Object newValue, Class requiredType, PropertyEditor pe, Object oldValue) {
		Object convertedValue = newValue;

		if (pe != null && !(convertedValue instanceof String)) {
			// Not a String -> use PropertyEditor's setValue.
			// With standard PropertyEditors, this will return the very same object;
			// we just want to allow special PropertyEditors to override setValue
			// for type conversion from non-String values to the required type.
			pe.setValue(convertedValue);
			Object newConvertedValue = pe.getValue();
			if (newConvertedValue != convertedValue) {
				convertedValue = newConvertedValue;
				// Reset PropertyEditor: It already did a proper conversion.
				// Don't use it again for a setAsText call.
				pe = null;
			}
		}

		if (requiredType != null && !requiredType.isArray() && convertedValue instanceof String[]) {
			// Convert String array to a comma-separated String.
			// Only applies if no PropertyEditor converted the String array before.
			// The CSV String will be passed into a PropertyEditor's setAsText method, if any.
			if (logger.isDebugEnabled()) {
				logger.debug("Converting String array to comma-delimited String [" + convertedValue + "]");
			}
			convertedValue = StringUtils.arrayToCommaDelimitedString((String[]) convertedValue);
		}

		if (pe != null && convertedValue instanceof String) {
			// Use PropertyEditor's setAsText in case of a String value.
			if (logger.isDebugEnabled()) {
				logger.debug("Converting String to [" + requiredType + "] using property editor [" + pe + "]");
			}
			pe.setValue(oldValue);
			pe.setAsText((String) convertedValue);
			convertedValue = pe.getValue();
		}

		return convertedValue;
	}

	protected Object convertToTypedArray(Object input, String propertyName, Class componentType) {
		if (input instanceof Collection) {
			// Convert Collection elements to array elements.
			Collection coll = (Collection) input;
			Object result = Array.newInstance(componentType, coll.size());
			int i = 0;
			for (Iterator it = coll.iterator(); it.hasNext(); i++) {
				Object value = doTypeConversionIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, it.next(), componentType);
				Array.set(result, i, value);
			}
			return result;
		}
		else if (input != null && input.getClass().isArray()) {
			// Convert Collection elements to array elements.
			int arrayLength = Array.getLength(input);
			Object result = Array.newInstance(componentType, arrayLength);
			for (int i = 0; i < arrayLength; i++) {
				Object value = doTypeConversionIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, Array.get(input, i), componentType);
				Array.set(result, i, value);
			}
			return result;
		}
		else {
			// A plain value: convert it to an array with a single component.
			Object result = Array.newInstance(componentType, 1);
			Object value = doTypeConversionIfNecessary(
					buildIndexedPropertyName(propertyName, 0), null, input, componentType);
			Array.set(result, 0, value);
			return result;
		}
	}

	protected Collection convertToTypedCollection(
			Collection original, String propertyName, MethodParameter methodParam) {

		Class elementType = null;
		if (JdkVersion.getMajorJavaVersion() >= JdkVersion.JAVA_15) {
			elementType = GenericsHelper.getCollectionParameterType(methodParam);
		}
		if (elementType != null) {
			Collection convertedCopy = (Collection) BeanUtils.instantiateClass(original.getClass());
			int i = 0;
			for (Iterator it = original.iterator(); it.hasNext(); i++) {
				Object convertedElement = doTypeConversionIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, it.next(), elementType);
				convertedCopy.add(convertedElement);
			}
			return convertedCopy;
		}
		return original;
	}

	protected Map convertToTypedMap(Map original, String propertyName, MethodParameter methodParam) {
		Class mapKeyType = null;
		Class mapValueType = null;
		if (JdkVersion.getMajorJavaVersion() >= JdkVersion.JAVA_15) {
			mapKeyType = GenericsHelper.getMapKeyParameterType(methodParam);
			mapValueType = GenericsHelper.getMapValueParameterType(methodParam);
		}
		if (mapKeyType != null || mapValueType != null) {
			Map convertedCopy = (Map) BeanUtils.instantiateClass(original.getClass());
			for (Iterator it = original.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				Object key = entry.getKey();
				Object convertedMapKey = doTypeConversionIfNecessary(
						buildKeyedPropertyName(propertyName, key), null, entry.getKey(), mapKeyType);
				Object convertedMapValue = doTypeConversionIfNecessary(
						buildKeyedPropertyName(propertyName, key), null, entry.getValue(), mapValueType);
				convertedCopy.put(convertedMapKey, convertedMapValue);
			}
			return convertedCopy;
		}
		return original;
	}

	private String buildIndexedPropertyName(String propertyName, int index) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + index + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	private String buildKeyedPropertyName(String propertyName, Object key) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + key + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

}
