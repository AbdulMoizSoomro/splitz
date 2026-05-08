# Makefile for Splitz CI readiness

.PHONY: ready-for-ci lint lint-fix

ready-for-ci:
	@echo "--- [1/3] LINTING & FORMATTING ---"
	@mvn -B spotless:apply checkstyle:check -Dstyle.color=never > ci-lint.log 2>&1 || ( \
		grep -E "\[ERROR\]|\[INFO\] .* \.{3,} FAILURE" ci-lint.log; \
		echo "❌ LINT FAILURE. Check ci-lint.log for details."; exit 1)
	@echo "✅ Linting & Formatting passed."

	@echo "--- [2/3] COMPILING ---"
	@mvn -B compile -Dstyle.color=never > ci-compile.log 2>&1 || ( \
		grep -E "\[ERROR\]|\[INFO\] .* \.{3,} FAILURE" ci-compile.log; \
		SERVICE=$$(grep "FAILURE" ci-compile.log | head -n 1 | awk '{print $$2}'); \
		echo "\n❌ $$SERVICE service failed. Check ci-compile.log for details."; exit 1)
	@echo "✅ Compilation successful."

	@echo "--- [3/3] RUNNING TESTS ---"
	@mvn -B verify -Dstyle.color=never -Dsurefire.useFile=false > ci-test.log 2>&1 || ( \
		grep -E "\[ERROR\] Tests run:|Failures: |Errors: |Skipped: |\[INFO\] .* \.{3,} FAILURE" ci-test.log | grep -v "SUCCESS"; \
		echo "\n❌ TEST FAILURE. Check ci-test.log for details."; exit 1)
	@grep -E "Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: 0" ci-test.log | tail -n 1
	@echo "\n✅ ALL CHECKS PASSED: Code is ready to be pushed."

