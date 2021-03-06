/*
 * Copyright (c) 2019-2020 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
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

package monix.bio

import cats.effect.{ExitCode, IO => CIO}
import minitest.SimpleTestSuite
import monix.bio.IO.Options
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Promise

object BIOAppSuite extends SimpleTestSuite {
  testAsync("run works") {
    val wasExecuted = Promise[Boolean]()
    val app = new BIOApp {
      override def run(args: List[String]) =
        UIO {
          wasExecuted.success(args.headOption.getOrElse("unknown") == "true")
          ExitCode.Success
        }
    }

    app.main(Array("true"))
    for (f <- wasExecuted.future) yield {
      assert(f, "wasExecuted")
    }
  }

  testAsync("options are configurable") {
    val opts = IO.defaultOptions
    assert(!opts.localContextPropagation, "!opts.localContextPropagation")
    val opts2 = opts.enableLocalContextPropagation
    assert(opts2.localContextPropagation, "opts2.localContextPropagation")
    val p = Promise[Options]()

    val app = new BIOApp {
      override val options = opts2

      def run(args: List[String]): UIO[ExitCode] =
        for (opts <- IO.readOptions) yield {
          p.success(opts)
          ExitCode.Success
        }
    }

    app.main(Array.empty)
    for (r <- p.future) yield {
      assertEquals(r, opts2)
    }
  }

  testAsync("ConcurrentEffect[Task]") {
    val wasExecuted = Promise[Boolean]()
    val app = new BIOApp {
      def run(args: List[String]): UIO[ExitCode] = {
        Task
          .from(
            Task
              .async[ExitCode] { cb =>
                wasExecuted.success(true)
                cb.onSuccess(ExitCode.Success)
              }
              .executeAsync
              .to[CIO]
          )
          .hideErrors
      }
    }

    app.main(Array("true"))
    for (r <- wasExecuted.future) yield {
      assert(r, "wasExecuted == true")
    }
  }
}
