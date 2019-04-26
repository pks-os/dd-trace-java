import com.netflix.hystrix.HystrixObservableCommand
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import io.opentracing.tag.Tags
import rx.Observable

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class HystrixObservableChainTest extends AgentTestRunner {
  // Uncomment for debugging:
  // static {
  //  System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  // }

  def "test command #action"() {
    setup:
    def command = new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
      @Trace
      private String tracedMethod() {
        return "Hello"
      }

      @Override
      protected Observable<String> construct() {
        Observable.defer {
          Observable.just(tracedMethod())
        }
      }
    }

    def result = runUnderTrace("parent") {
      command.toObservable().map {
        it.toUpperCase()
      }.flatMap { str ->
        new HystrixObservableCommand<String>(asKey("OtherGroup")) {
          @Trace
          private String tracedMethod() {
            return "$str!"
          }

          @Override
          protected Observable<String> construct() {
            Observable.defer {
              Observable.just(tracedMethod())
            }
          }
        }.toObservable()
      }.toBlocking().first()
    }

    expect:
    result == "HELLO!"

    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "parent"
          resourceName "parent"
          spanType null
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "hystrix.cmd"
          resourceName "ExampleGroup.HystrixObservableChainTest\$1.execute"
          spanType null
          childOf span(0)
          errored false
          tags {
            "hystrix.command" "HystrixObservableChainTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
            "$Tags.COMPONENT.key" "hystrix"
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName "hystrix.cmd"
          resourceName "OtherGroup.HystrixObservableChainTest\$2.execute"
          spanType null
          childOf span(1)
          errored false
          tags {
            "hystrix.command" "HystrixObservableChainTest\$2"
            "hystrix.group" "OtherGroup"
            "hystrix.circuit-open" false
            "$Tags.COMPONENT.key" "hystrix"
            defaultTags()
          }
        }
        span(3) {
          serviceName "unnamed-java-app"
          operationName "HystrixObservableChainTest\$2.tracedMethod"
          resourceName "HystrixObservableChainTest\$2.tracedMethod"
          spanType null
          childOf span(2)
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
        span(4) {
          serviceName "unnamed-java-app"
          operationName "HystrixObservableChainTest\$1.tracedMethod"
          resourceName "HystrixObservableChainTest\$1.tracedMethod"
          spanType null
          childOf span(1)
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
      }
    }
  }
}
