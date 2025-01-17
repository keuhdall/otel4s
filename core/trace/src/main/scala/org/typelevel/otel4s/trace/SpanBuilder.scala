/*
 * Copyright 2022 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.typelevel.otel4s
package trace

import cats.Applicative
import cats.arrow.FunctionK
import cats.effect.Resource

import scala.concurrent.duration.FiniteDuration

trait SpanBuilder[F[_]] {

  /** Adds an attribute to the newly created span. If [[SpanBuilder]] previously
    * contained a mapping for the key, the old value is replaced by the
    * specified value.
    *
    * @param attribute
    *   the attribute to associate with the span
    */
  def addAttribute[A](attribute: Attribute[A]): SpanBuilder[F]

  /** Adds attributes to the [[SpanBuilder]]. If the SpanBuilder previously
    * contained a mapping for any of the keys, the old values are replaced by
    * the specified values.
    *
    * @param attributes
    *   the set of attributes to associate with the span
    */
  def addAttributes(attributes: Attribute[_]*): SpanBuilder[F]

  /** Adds a link to the newly created span.
    *
    * Links are used to link spans in different traces. Used (for example) in
    * batching operations, where a single batch handler processes multiple
    * requests from different traces or the same trace.
    *
    * @param spanContext
    *   the context of the linked span
    *
    * @param attributes
    *   the set of attributes to associate with the link
    */
  def addLink(
      spanContext: SpanContext,
      attributes: Attribute[_]*
  ): SpanBuilder[F]

  /** Sets the finalization strategy for the newly created span.
    *
    * The span finalizers are executed upon resource finalization.
    *
    * The default strategy is [[SpanFinalizer.Strategy.reportAbnormal]].
    *
    * @param strategy
    *   the strategy to apply upon span finalization
    */
  def withFinalizationStrategy(strategy: SpanFinalizer.Strategy): SpanBuilder[F]

  /** Sets the [[SpanKind]] for the newly created span. If not called, the
    * implementation will provide a default value [[SpanKind.Internal]].
    *
    * @param spanKind
    *   the kind of the newly created span
    */
  def withSpanKind(spanKind: SpanKind): SpanBuilder[F]

  /** Sets an explicit start timestamp for the newly created span.
    *
    * Use this method to specify an explicit start timestamp. If not called, the
    * implementation will use the timestamp value from the method called on
    * [[build]], which should be the default case.
    *
    * '''Note''': the timestamp should be based on `Clock[F].realTime`. Using
    * `Clock[F].monotonic` may lead to a missing span.
    *
    * @param timestamp
    *   the explicit start timestamp from the epoch
    */
  def withStartTimestamp(timestamp: FiniteDuration): SpanBuilder[F]

  /** Indicates that the span should be the root one and the scope parent should
    * be ignored.
    */
  def root: SpanBuilder[F]

  /** Sets the parent to use from the specified [[SpanContext]]. If not set, the
    * span that is currently available in the scope will be used as parent.
    *
    * '''Note''': if called multiple times, only the last specified value will
    * be used.
    *
    * '''Note''': the previous call of [[root]] will be ignored.
    *
    * @param parent
    *   the span context to use as a parent
    */
  def withParent(parent: SpanContext): SpanBuilder[F]

  def build: SpanOps[F]
}

object SpanBuilder {

  def noop[F[_]: Applicative](back: Span.Backend[F]): SpanBuilder[F] =
    new SpanBuilder[F] {
      private val span: Span[F] = Span.fromBackend(back)

      def addAttribute[A](attribute: Attribute[A]): SpanBuilder[F] = this

      def addAttributes(attributes: Attribute[_]*): SpanBuilder[F] = this

      def addLink(ctx: SpanContext, attributes: Attribute[_]*): SpanBuilder[F] =
        this

      def root: SpanBuilder[F] = this

      def withFinalizationStrategy(
          strategy: SpanFinalizer.Strategy
      ): SpanBuilder[F] = this

      def withParent(parent: SpanContext): SpanBuilder[F] = this

      def withSpanKind(spanKind: SpanKind): SpanBuilder[F] = this

      def withStartTimestamp(timestamp: FiniteDuration): SpanBuilder[F] = this

      def build: SpanOps[F] = new SpanOps[F] {
        def startUnmanaged: F[Span[F]] =
          Applicative[F].pure(span)

        def resource: Resource[F, SpanOps.Res[F]] =
          Resource.pure(SpanOps.Res(span, FunctionK.id))

        def use[A](f: Span[F] => F[A]): F[A] = f(span)

        override def use_ : F[Unit] = Applicative[F].unit
      }
    }

}
