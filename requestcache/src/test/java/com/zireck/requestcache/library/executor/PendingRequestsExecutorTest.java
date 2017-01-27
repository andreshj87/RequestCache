package com.zireck.requestcache.library.executor;

import com.zireck.requestcache.library.cache.RequestQueue;
import com.zireck.requestcache.library.model.RequestModel;
import com.zireck.requestcache.library.network.NetworkRequestManager;
import com.zireck.requestcache.library.network.NetworkResponseCallback;
import com.zireck.requestcache.library.util.MethodType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class) public class PendingRequestsExecutorTest {

  private PendingRequestsExecutor pendingRequestsExecutor;

  @Mock private NetworkRequestManager mockNetworkRequestManager;

  @Before public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    pendingRequestsExecutor = new PendingRequestsExecutor(mockNetworkRequestManager);
  }

  @Test public void shouldNotBeExecutingRightAfterItsInstantiated() throws Exception {
    boolean isExecuting = pendingRequestsExecutor.isExecuting();

    assertThat(isExecuting, is(false));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionWhenNegativeIntervalTimeGiven() throws Exception {
    pendingRequestsExecutor.setIntervalTime(-1);
  }

  @Test public void shouldNotExecuteWhenNullQueueGiven() throws Exception {
    RequestQueue nullRequestQueue = null;

    boolean executeResult = pendingRequestsExecutor.execute(nullRequestQueue);

    verifyZeroInteractions(nullRequestQueue);
    assertThat(executeResult, is(false));
  }

  @Test public void shouldNotExecuteWhenEmptyQueueGiven() throws Exception {
    RequestQueue mockRequestQueue = mock(RequestQueue.class);
    when(mockRequestQueue.isEmpty()).thenReturn(true);
    when(mockRequestQueue.hasNext()).thenReturn(false);

    pendingRequestsExecutor.execute(mockRequestQueue);

    verify(mockRequestQueue).load();
    verify(mockRequestQueue).isEmpty();
    verify(mockRequestQueue, atMost(1)).hasNext();
    verifyNoMoreInteractions(mockRequestQueue);
    assertThat(pendingRequestsExecutor.isExecuting(), is(false));
  }

  @Test public void shouldSendRequestWhenExecutingNonEmptyQueue() throws Exception {
    RequestQueue mockRequestQueue = mock(RequestQueue.class);
    when(mockRequestQueue.isEmpty()).thenReturn(false);
    when(mockRequestQueue.hasNext()).thenReturn(true);
    RequestModel mockRequestModel = mock(RequestModel.class);
    when(mockRequestQueue.next()).thenReturn(mockRequestModel);

    boolean executeResult = pendingRequestsExecutor.execute(mockRequestQueue);

    verify(mockRequestQueue).next();
    verify(mockNetworkRequestManager).sendRequest(eq(mockRequestModel),
        any(NetworkResponseCallback.class));
    assertThat(executeResult, is(true));
    assertThat(pendingRequestsExecutor.isExecuting(), is(true));
  }

  @Test public void shouldProperlyHandleSuccessfulResponse() throws Exception {
    ArgumentCaptor<NetworkResponseCallback> networkResponseCallbackArgumentCaptor =
        ArgumentCaptor.forClass(NetworkResponseCallback.class);
    RequestQueue mockRequestQueue = mock(RequestQueue.class);
    when(mockRequestQueue.isEmpty()).thenReturn(false);
    when(mockRequestQueue.hasNext()).thenReturn(true);

    pendingRequestsExecutor.execute(mockRequestQueue);

    verify(mockNetworkRequestManager).sendRequest(any(RequestModel.class),
        networkResponseCallbackArgumentCaptor.capture());
    networkResponseCallbackArgumentCaptor.getValue().onSuccess();
    verify(mockRequestQueue).remove();
    verify(mockRequestQueue).persist();
    verifyNoMoreInteractions(mockRequestQueue);
  }

  private RequestModel getSomeRequestModel() {
    return new RequestModel.Builder<String>().methodType(MethodType.POST)
        .baseUrl("https://api.github.com/")
        .endpoint("users/GigigoGreenLabs/repos")
        .body("This is the body")
        .build();
  }
}