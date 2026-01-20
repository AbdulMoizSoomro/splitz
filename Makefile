# Makefile for Splitz CI readiness

.PHONY: ready-for-ci lint lint-fix

ready-for-ci:
	@echo "--- [1/3] LINTING ---"
	@OUTPUT=$$(mvn spotless:check checkstyle:check -Dstyle.color=never 2>&1); \
	if [ $$? -ne 0 ]; then \
		echo "$$OUTPUT" | grep -E "\[INFO\] .* \.{3,} FAILURE"; \
		echo "❌ LINT FAILURE. Run 'make lint-fix' to resolve."; exit 1; \
	fi; \
	echo "$$OUTPUT" | grep -E "\[INFO\] .* \.{3,} SUCCESS" | tail -n 1

	@echo "--- [2/3] COMPILING ---"
	@OUTPUT=$$(mvn compile -Dstyle.color=never 2>&1); \
	if [ $$? -ne 0 ]; then \
		echo "$$OUTPUT" | grep -E "\[INFO\] .* \.{3,} FAILURE"; \
		SERVICE=$$(echo "$$OUTPUT" | grep "FAILURE" | head -n 1 | awk '{print $$2}'); \
		echo "\n❌ $$SERVICE service failed."; exit 1; \
	fi; \
	echo "$$OUTPUT" | grep -E "\[INFO\] .* \.{3,} SUCCESS" | tail -n 1

	@echo "--- [3/3] RUNNING TESTS ---"
	@mvn test | grep -E "Tests run: |Failures: |Errors: |Skipped: |BUILD SUCCESS|BUILD FAILURE" || (echo "\n❌ TEST FAILURE: One or more tests failed." && exit 1)
	@echo "\n✅ ALL CHECKS PASSED: Code is ready to be pushed."

lint:
	@mvn spotless:check checkstyle:check

lint-fix:
	@mvn spotless:apply
