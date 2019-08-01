package com.wfsample.packaging;

import com.google.protobuf.ByteString;

import com.wavefront.config.ReportingUtils;
import com.wavefront.config.WavefrontReportingConfig;
import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.grpc.WavefrontServerTracerFactory;
import com.wavefront.sdk.grpc.reporter.WavefrontGrpcReporter;
import com.wfsample.beachshirts.GiftPack;
import com.wfsample.beachshirts.PackagingGrpc;
import com.wfsample.beachshirts.PackedShirts;
import com.wfsample.beachshirts.Void;
import com.wfsample.beachshirts.WrapRequest;
import com.wfsample.beachshirts.WrappingType;
import com.wfsample.beachshirts.WrappingTypes;
import com.wfsample.common.BeachShirtsUtils;
import com.wfsample.common.GrpcServiceConfig;

import org.apache.commons.lang3.BooleanUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * Driver for the packaging service which packs the a given shirt.
 *
 * @author Srujan Narkedamalli (snarkedamall@wavefront.com).
 */
public class PackagingService {

  public PackagingService(GrpcServiceConfig config) throws Exception {
    ApplicationTags applicationTags = ReportingUtils.constructApplicationTags
        (config.getApplicationTagsYamlFile());
    WavefrontReportingConfig wfReportingConfig = ReportingUtils.constructWavefrontReportingConfig(
        config.getWfReportingConfigYamlFile());
    String source = wfReportingConfig.getSource();
    WavefrontSender wavefrontSender = ReportingUtils.constructWavefrontSender(wfReportingConfig);
    WavefrontTracer tracer;
    if (BooleanUtils.isTrue(wfReportingConfig.getReportTraces())) {
      WavefrontSpanReporter wfSpanReporter = new WavefrontSpanReporter.Builder().withSource(source).build(wavefrontSender);
      tracer = (new com.wavefront.opentracing.WavefrontTracer.Builder(wfSpanReporter, applicationTags)).build();
    } else {
      tracer = null;
    }
    WavefrontJvmReporter wfJvmReporter = new WavefrontJvmReporter.Builder(applicationTags).
        withSource(source).build(wavefrontSender);
    wfJvmReporter.start();
    WavefrontGrpcReporter grpcReporter = new WavefrontGrpcReporter.Builder(
        applicationTags).withSource(source).reportingIntervalSeconds(30).build(wavefrontSender);
    grpcReporter.start();
    WavefrontServerTracerFactory tracerFactory =
        new WavefrontServerTracerFactory.Builder(grpcReporter, applicationTags).
            withTracer(tracer).recordStreamingStats().build();
    ServerBuilder builder = ServerBuilder.forPort(config.getGrpcPort()).
        addService(new PackagingImpl()).addStreamTracerFactory(tracerFactory);
    Server packaging = builder.build();
    System.out.println("Starting Packaging server ...");
    packaging.start();
    System.out.println("Packaging server started");
    packaging.awaitTermination();
  }

  public static void main(String[] args) throws Exception {
    GrpcServiceConfig configuration = BeachShirtsUtils.scenarioFromFile(args[0]);
    new PackagingService(configuration);
  }

  static class PackagingImpl extends PackagingGrpc.PackagingImplBase {
    private final AtomicInteger getTypes = new AtomicInteger(0);
    private final AtomicInteger restock = new AtomicInteger(0);
    private final AtomicInteger wrap = new AtomicInteger(0);
    private final AtomicInteger giftWrap = new AtomicInteger(0);
    private final Random rand = new Random(0L);

    @Override
    public void wrapShirts(WrapRequest request, StreamObserver<PackedShirts> responseObserver) {
      try {
        Thread.sleep((long) (rand.nextGaussian() * 70 + 100));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (wrap.incrementAndGet() % 40 == 0) {
        // can't pack more than 10 shirts at once.
        responseObserver.onError(Status.INTERNAL.asRuntimeException());
      }
      responseObserver.onNext(PackedShirts.newBuilder().
          addAllShirts(request.getShirtsList()).
          build());
      responseObserver.onCompleted();
    }

    @Override
    public void giftWrap(WrapRequest request, StreamObserver<GiftPack> responseObserver) {
      if (giftWrap.incrementAndGet() % 30 == 0) {
        responseObserver.onError(Status.INTERNAL.asRuntimeException());
      }
      try {
        Thread.sleep((long) (rand.nextGaussian() * 70 + 100));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (request.getShirtsCount() > 40) {
        int resp = (int) Math.round(rand.nextDouble() *
            100.0) + 10000;
        try {
          Thread.sleep((long) (rand.nextGaussian() * 200 + 1400));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        ByteString byteString = ByteString.copyFrom(new byte[resp]);
        responseObserver.onNext(GiftPack.newBuilder().setGiftMaterial(byteString).build());
        responseObserver.onCompleted();
      } else {
        int resp = (int) Math.round(rand.nextDouble() * 100.0);
        ByteString byteString = ByteString.copyFrom(new byte[resp]);
        responseObserver.onNext(GiftPack.newBuilder().setGiftMaterial(byteString).build());
        responseObserver.onCompleted();
      }
    }

    @Override
    public void restockMaterial(WrappingType request,
                                StreamObserver<com.wfsample.beachshirts.Status> responseObserver) {
      try {
        Thread.sleep((long) (rand.nextGaussian() * 70 + 100));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (restock.incrementAndGet() % 20 == 0) {
        responseObserver.onError(Status.INTERNAL.asRuntimeException());
      } else {
        responseObserver.onNext(com.wfsample.beachshirts.Status.newBuilder().
            setStatus(true).build());
        responseObserver.onCompleted();
      }
    }

    @Override
    public void getPackingTypes(Void request, StreamObserver<WrappingTypes> responseObserver) {
      try {
        Thread.sleep((long) (rand.nextGaussian() * 70 + 100));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (getTypes.incrementAndGet() % 30 == 0) {
        responseObserver.onError(Status.INTERNAL.asRuntimeException());
      } else {
        responseObserver.onNext(WrappingTypes.newBuilder().addWrappingType(WrappingType.
            newBuilder().setWrappingType("wrap wrap wrap wrap").build()).build());
        responseObserver.onCompleted();
      }
    }
  }
}