package com.chatbot.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  @Test
  void setsAnEightCharIdDuringTheRequestThenClearsIt() throws Exception {
    CorrelationIdFilter filter = new CorrelationIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] idSeenInsideChain = new String[1];
    filter.doFilter(
        request,
        response,
        (req, res) -> idSeenInsideChain[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID));

    assertThat(idSeenInsideChain[0]).isNotNull().hasSize(8);
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID)).isNull();
  }
}
