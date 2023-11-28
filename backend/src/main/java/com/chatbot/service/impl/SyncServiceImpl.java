package com.chatbot.service.impl;

import com.chatbot.beans.SyncLog;
import com.chatbot.errorhandling.NonRetryableException;
import com.chatbot.errorhandling.RetryPolicy;
import com.chatbot.messaging.FailedBatchProducer;
import com.chatbot.repository.MovieRepository;
import com.chatbot.repository.SyncLogRepository;
import com.chatbot.service.MovieBackFillService;
import com.chatbot.service.SyncService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SyncServiceImpl implements SyncService {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

  private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

  private final SyncLogRepository syncLogRepository;
  private final MovieBackFillService movieBackFillService;
  private final FailedBatchProducer failedBatchProducer;
  private final MovieRepository movieRepository;

  @Value("${sync.default-start-date:01-01-2020}")
  private String defaultStartDateStr;

  @Value("${sync.thread-count:5}")
  private int threadCount;

  @Autowired
  public SyncServiceImpl(SyncLogRepository syncLogRepository,
                         MovieBackFillService movieBackFillService,
                         FailedBatchProducer failedBatchProducer,
                         MovieRepository movieRepository) {
    this.syncLogRepository = syncLogRepository;
    this.movieBackFillService = movieBackFillService;
    this.failedBatchProducer = failedBatchProducer;
    this.movieRepository = movieRepository;
  }

  @Override
  public SyncLog getStatus() {
    return syncLogRepository.findById(1L).orElseGet(() -> {
      SyncLog inferred = new SyncLog();
      movieRepository.findMaxReleaseDate().ifPresent(inferred::setLastSyncDate);
      return inferred;
    });
  }

  @Override
  public boolean isSyncInProgress() {
    return syncInProgress.get();
  }

  @Override
  public void syncNowAsync() {
    if (!syncInProgress.compareAndSet(false, true)) {
      log.info("Sync already in progress - ignoring trigger");
      return;
    }
    CompletableFuture.runAsync(() -> {
      try {
        syncNow();
      } catch (Exception e) {
        log.warn("Async sync failed: {}", e.getMessage());
      }
    });
  }

  @Override
  public synchronized SyncLog syncNow() {
    syncInProgress.set(true);
    try {
    SyncLog syncLog = syncLogRepository.findById(1L).orElse(new SyncLog());

    LocalDate startDate;
    if (syncLog.getLastSyncDate() != null) {
      startDate = syncLog.getLastSyncDate().plusDays(1);
    } else {
      startDate = movieRepository.findMaxReleaseDate()
          .map(d -> d.plusDays(1))
          .orElse(LocalDate.parse(defaultStartDateStr, FMT));
      log.info("No sync record found - inferring start date from DB: {}", startDate.format(FMT));
    }

    LocalDate endDate = LocalDate.now().minusDays(1);

    if (startDate.isAfter(endDate)) {
      log.info("Already up to date (last sync: {})", syncLog.getLastSyncDate());
      return syncLog;
    }

    List<LocalDate[]> batches = buildMonthlyBatches(startDate, endDate);

    log.info("{} monthly batches to process with {} threads", batches.size(), threadCount);

    movieBackFillService.addLanguagesToDatabase();
    log.info("Languages done");

    movieBackFillService.addGenresToDatabase();
    log.info("Genres done");

    AtomicBoolean[] successes = new AtomicBoolean[batches.size()];
    IntStream.range(0, batches.size()).forEach(i -> successes[i] = new AtomicBoolean(false));

    java.util.Map<String, String> parentContext = org.slf4j.MDC.getCopyOfContextMap();
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    List<CompletableFuture<Void>> futures = IntStream.range(0, batches.size())
        .mapToObj(i -> {
          LocalDate[] batch = batches.get(i);
          return CompletableFuture.runAsync(() -> {
            if (parentContext != null) {
              org.slf4j.MDC.setContextMap(parentContext);
            }
            log.info("Batch [{}/{}] {} -> {}",
                i + 1, batches.size(), batch[0].format(FMT), batch[1].format(FMT));
            List<long[]> genrePairs = movieBackFillService.addMoviesToDatabase(
                batch[0].format(FMT), batch[1].format(FMT));
            movieBackFillService.saveMovieGenres(genrePairs);
            successes[i].set(true);
            log.info("Batch [{}/{}] done", i + 1, batches.size());
          }, executor).exceptionally(e -> {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String error = cause.getMessage();
            if (cause instanceof NonRetryableException) {
              successes[i].set(true);
              log.info("Batch [{}/{}] already in database - counted as synced",
                  i + 1, batches.size());
            } else if (RetryPolicy.isRetryable(cause)) {
              log.warn("Batch [{}/{}] failed: {} - queuing for retry",
                  i + 1, batches.size(), error);
              failedBatchProducer.publish(batch[0].format(FMT), batch[1].format(FMT), error);
            } else {
              log.warn("Batch [{}/{}] skipped (non-retryable): {}",
                  i + 1, batches.size(), error);
            }
            return null;
          });
        })
        .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    executor.shutdown();

    LocalDate lastSuccessDate = null;
    for (int i = 0; i < batches.size(); i++) {
      if (successes[i].get()) {
        lastSuccessDate = batches.get(i)[1];
      } else {
        log.warn("Batch [{}/{}] incomplete - subsequent batches will not be marked done",
            i + 1, batches.size());
        break;
      }
    }

    if (lastSuccessDate != null) {
      syncLog.setId(1L);
      syncLog.setLastSyncDate(lastSuccessDate);
      syncLog.setSyncedAt(LocalDateTime.now());
      syncLogRepository.save(syncLog);
      log.info("Complete - synced through {}", lastSuccessDate.format(FMT));
    } else {
      log.warn("No batches succeeded - lastSyncDate unchanged");
    }

    return syncLog;
    } finally {
      syncInProgress.set(false);
    }
  }

  private List<LocalDate[]> buildMonthlyBatches(LocalDate startDate, LocalDate endDate) {
    List<LocalDate[]> batches = new ArrayList<>();
    LocalDate batchStart = startDate;
    while (!batchStart.isAfter(endDate)) {
      LocalDate batchEnd = batchStart.withDayOfMonth(batchStart.lengthOfMonth());
      if (batchEnd.isAfter(endDate)) batchEnd = endDate;
      batches.add(new LocalDate[]{batchStart, batchEnd});
      batchStart = batchEnd.plusDays(1);
    }
    return batches;
  }
}
