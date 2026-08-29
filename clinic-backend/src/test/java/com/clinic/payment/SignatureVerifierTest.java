package com.clinic.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhook signature verification is the only thing standing between the
 * clinic's books and anyone who can find the webhook URL, so it gets tested for
 * what it rejects rather than only for what it accepts.
 */
class SignatureVerifierTest {

    private static final String SECRET = "webhook-secret-value";
    private static final String BODY = """
            {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_1"}}}}""";

    @Test
    void acceptsASignatureItProduced() {
        assertThat(SignatureVerifier.matches(BODY, SECRET, SignatureVerifier.sign(BODY, SECRET))).isTrue();
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() {
        String forged = SignatureVerifier.sign(BODY, "some-other-secret");

        assertThat(SignatureVerifier.matches(BODY, SECRET, forged)).isFalse();
    }

    @Test
    void rejectsAGenuineSignatureAttachedToATamperedBody() {
        // The attack this stops: replay a real "captured" signature against a
        // body naming a different order.
        String signature = SignatureVerifier.sign(BODY, SECRET);
        String tampered = BODY.replace("order_1", "order_2");

        assertThat(SignatureVerifier.matches(tampered, SECRET, signature)).isFalse();
    }

    @Test
    void rejectsAMissingOrEmptySignature() {
        assertThat(SignatureVerifier.matches(BODY, SECRET, null)).isFalse();
        assertThat(SignatureVerifier.matches(BODY, SECRET, "")).isFalse();
        assertThat(SignatureVerifier.matches(BODY, SECRET, "   ")).isFalse();
    }

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() {
        // A blank secret must fail closed. Failing open would accept anything.
        assertThat(SignatureVerifier.matches(BODY, "", "anything")).isFalse();
        assertThat(SignatureVerifier.matches(BODY, null, "anything")).isFalse();
    }

    @Test
    void toleratesSurroundingWhitespaceInTheHeader() {
        String signature = " " + SignatureVerifier.sign(BODY, SECRET) + " ";

        assertThat(SignatureVerifier.matches(BODY, SECRET, signature)).isTrue();
    }

    @Test
    void producesAStableHexSignature() {
        String first = SignatureVerifier.sign(BODY, SECRET);

        assertThat(first).isEqualTo(SignatureVerifier.sign(BODY, SECRET));
        assertThat(first).matches("[0-9a-f]{64}");
    }
}
