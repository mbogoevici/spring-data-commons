/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.data.repository.query;

import static org.springframework.data.repository.query.ReactiveWrappers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.repository.util.QueryExecutionConverters;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.Single;

/**
 * Conversion support for reactive wrapper types.
 * 
 * @author Mark Paluch
 * @since 2.0
 */
public abstract class ReactiveWrapperConverters {

	private static final List<AbstractReactiveWrapper<?>> REACTIVE_WRAPPERS = new ArrayList<>();
	private static final GenericConversionService GENERIC_CONVERSION_SERVICE = new GenericConversionService();

	static {

		if (RXJAVA1_PRESENT) {
			REACTIVE_WRAPPERS.add(RxJava1SingleWrapper.INSTANCE);
			REACTIVE_WRAPPERS.add(RxJava1ObservableWrapper.INSTANCE);
		}

		if (RXJAVA2_PRESENT) {
			REACTIVE_WRAPPERS.add(RxJava2SingleWrapper.INSTANCE);
			REACTIVE_WRAPPERS.add(RxJava2MaybeWrapper.INSTANCE);
			REACTIVE_WRAPPERS.add(RxJava2ObservableWrapper.INSTANCE);
			REACTIVE_WRAPPERS.add(RxJava2FlowableWrapper.INSTANCE);
		}

		if (PROJECT_REACTOR_PRESENT) {
			REACTIVE_WRAPPERS.add(FluxWrapper.INSTANCE);
			REACTIVE_WRAPPERS.add(MonoWrapper.INSTANCE);
			REACTIVE_WRAPPERS.add(PublisherWrapper.INSTANCE);
		}

		QueryExecutionConverters.registerConvertersIn(GENERIC_CONVERSION_SERVICE);
	}

	private ReactiveWrapperConverters() {

	}

	/**
	 * Returns whether the given type is a supported wrapper type.
	 * 
	 * @param type must not be {@literal null}.
	 * @return
	 */
	public static boolean supports(Class<?> type) {
		return assignableStream(type).isPresent();
	}

	/**
	 * Returns whether the type is a single-like wrapper.
	 * 
	 * @param type must not be {@literal null}.
	 * @return
	 * @see Single
	 * @see Mono
	 */
	public static boolean isSingleLike(Class<?> type) {
		return assignableStream(type).map(wrapper -> wrapper.getMultiplicity() == Multiplicity.ONE).orElse(false);
	}

	/**
	 * Returns whether the type is a collection/multi-element-like wrapper.
	 * 
	 * @param type must not be {@literal null}.
	 * @return
	 * @see Observable
	 * @see Flux
	 * @see Publisher
	 */
	public static boolean isCollectionLike(Class<?> type) {
		return assignableStream(type).map(wrapper -> wrapper.getMultiplicity() == Multiplicity.MANY).orElse(false);
	}

	/**
	 * Casts or converts the given wrapper type into a different wrapper type.
	 * 
	 * @param stream the stream, must not be {@literal null}.
	 * @param expectedWrapperType must not be {@literal null}.
	 * @return
	 */
	public static <T> T toWrapper(Object stream, Class<? extends T> expectedWrapperType) {

		Assert.notNull(stream, "Stream must not be null!");
		Assert.notNull(expectedWrapperType, "Converter must not be null!");

		if (expectedWrapperType.isAssignableFrom(stream.getClass())) {
			return (T) stream;
		}

		return GENERIC_CONVERSION_SERVICE.convert(stream, expectedWrapperType);
	}

	/**
	 * Maps elements of a reactive element stream to other elements.
	 * 
	 * @param stream must not be {@literal null}.
	 * @param converter must not be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> T map(Object stream, Converter<Object, Object> converter) {

		Assert.notNull(stream, "Stream must not be null!");
		Assert.notNull(converter, "Converter must not be null!");

		for (AbstractReactiveWrapper<?> reactiveWrapper : REACTIVE_WRAPPERS) {

			if (ClassUtils.isAssignable(reactiveWrapper.getWrapperClass(), stream.getClass())) {
				return (T) reactiveWrapper.map(stream, converter);
			}
		}

		throw new IllegalStateException(String.format("Cannot apply converter to %s", stream));
	}

	private static Optional<AbstractReactiveWrapper<?>> assignableStream(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		return findWrapper(wrapper -> ClassUtils.isAssignable(wrapper.getWrapperClass(), type));
	}

	private static Optional<AbstractReactiveWrapper<?>> findWrapper(
			Predicate<? super AbstractReactiveWrapper<?>> predicate) {

		return REACTIVE_WRAPPERS.stream().filter(predicate).findFirst();
	}

	private abstract static class AbstractReactiveWrapper<T> {

		private final Class<? super T> wrapperClass;
		private final Multiplicity multiplicity;

		public AbstractReactiveWrapper(Class<? super T> wrapperClass, Multiplicity multiplicity) {
			this.wrapperClass = wrapperClass;
			this.multiplicity = multiplicity;
		}

		public Class<? super T> getWrapperClass() {
			return wrapperClass;
		}

		public Multiplicity getMultiplicity() {
			return multiplicity;
		}

		public abstract Object map(Object wrapper, Converter<Object, Object> converter);
	}

	private static class MonoWrapper extends AbstractReactiveWrapper<Mono<?>> {

		static final MonoWrapper INSTANCE = new MonoWrapper();

		private MonoWrapper() {
			super(Mono.class, Multiplicity.ONE);
		}

		public Mono<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((Mono<?>) wrapper).map(converter::convert);
		}
	}

	private static class FluxWrapper extends AbstractReactiveWrapper<Flux<?>> {

		static final FluxWrapper INSTANCE = new FluxWrapper();

		private FluxWrapper() {
			super(Flux.class, Multiplicity.MANY);
		}

		public Flux<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((Flux<?>) wrapper).map(converter::convert);
		}
	}

	private static class PublisherWrapper extends AbstractReactiveWrapper<Publisher<?>> {

		static final PublisherWrapper INSTANCE = new PublisherWrapper();

		public PublisherWrapper() {
			super(Publisher.class, Multiplicity.MANY);
		}

		@Override
		public Publisher<?> map(Object wrapper, Converter<Object, Object> converter) {

			if (wrapper instanceof Mono) {
				return MonoWrapper.INSTANCE.map((Mono<?>) wrapper, converter);
			}

			if (wrapper instanceof Flux) {
				return FluxWrapper.INSTANCE.map((Flux<?>) wrapper, converter);
			}

			return FluxWrapper.INSTANCE.map(Flux.from((Publisher<?>) wrapper), converter);
		}
	}

	private static class RxJava1SingleWrapper extends AbstractReactiveWrapper<Single<?>> {

		static final RxJava1SingleWrapper INSTANCE = new RxJava1SingleWrapper();

		private RxJava1SingleWrapper() {
			super(Single.class, Multiplicity.ONE);
		}

		@Override
		public Single<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((Single<?>) wrapper).map(converter::convert);
		}
	}

	private static class RxJava1ObservableWrapper extends AbstractReactiveWrapper<Observable<?>> {

		static final RxJava1ObservableWrapper INSTANCE = new RxJava1ObservableWrapper();

		private RxJava1ObservableWrapper() {
			super(Observable.class, Multiplicity.MANY);
		}

		@Override
		public Observable<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((Observable<?>) wrapper).map(converter::convert);
		}
	}

	private static class RxJava2SingleWrapper extends AbstractReactiveWrapper<io.reactivex.Single<?>> {

		static final RxJava2SingleWrapper INSTANCE = new RxJava2SingleWrapper();

		private RxJava2SingleWrapper() {
			super(io.reactivex.Single.class, Multiplicity.ONE);
		}

		@Override
		public io.reactivex.Single<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((io.reactivex.Single<?>) wrapper).map(converter::convert);
		}
	}

	private static class RxJava2MaybeWrapper extends AbstractReactiveWrapper<io.reactivex.Maybe<?>> {

		static final RxJava2MaybeWrapper INSTANCE = new RxJava2MaybeWrapper();

		private RxJava2MaybeWrapper() {
			super(io.reactivex.Maybe.class, Multiplicity.MANY);
		}

		@Override
		public io.reactivex.Maybe<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((io.reactivex.Maybe<?>) wrapper).map(converter::convert);
		}
	}

	private static class RxJava2ObservableWrapper extends AbstractReactiveWrapper<io.reactivex.Observable<?>> {

		static final RxJava2ObservableWrapper INSTANCE = new RxJava2ObservableWrapper();

		private RxJava2ObservableWrapper() {
			super(io.reactivex.Observable.class, Multiplicity.MANY);
		}

		@Override
		public io.reactivex.Observable<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((io.reactivex.Observable<?>) wrapper).map(converter::convert);
		}
	}

	private static class RxJava2FlowableWrapper extends AbstractReactiveWrapper<io.reactivex.Flowable<?>> {

		static final RxJava2FlowableWrapper INSTANCE = new RxJava2FlowableWrapper();

		private RxJava2FlowableWrapper() {
			super(io.reactivex.Flowable.class, Multiplicity.MANY);
		}

		@Override
		public io.reactivex.Flowable<?> map(Object wrapper, Converter<Object, Object> converter) {
			return ((io.reactivex.Flowable<?>) wrapper).map(converter::convert);
		}
	}

	private enum Multiplicity {
		ONE, MANY,
	}

}
