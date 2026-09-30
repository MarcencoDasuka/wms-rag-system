package com.isd.wms.service;

import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentSearchRequest;
import com.isd.wms.dto.replenishment.ReplenishmentUpdateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplenishmentServiceTest {

    @Mock private ReplenishmentRepository replenishmentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private TransportUnitRepository transportUnitRepository;
    @Mock private ReplenishmentMapper replenishmentMapper;
    @Mock private WorkflowService workflowService;
    @Mock private TaskService taskService;
    @Mock private SecurityFacade securityFacade;
    @Mock private ImportService importService;

    @InjectMocks
    private ReplenishmentService replenishmentService;

    private Product product;
    private Location destinationLocation;
    private Replenishment replenishment;
    private ReplenishmentResponse response;

    @BeforeEach
    void setUp() {
        product = new Product("Widget", "WGT-01", null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);

        destinationLocation = new Location("Pick Face", "PICK-01", null, null, true);
        org.springframework.test.util.ReflectionTestUtils.setField(destinationLocation, "id", 3L);

        Task task = new Task(null, com.isd.wms.enums.TaskType.REPLENISHMENT, 10);
        org.springframework.test.util.ReflectionTestUtils.setField(task, "id", 1L);

        replenishment = new Replenishment(product, 10, destinationLocation);
        replenishment.setTask(task);
        org.springframework.test.util.ReflectionTestUtils.setField(replenishment, "id", 1L);
        replenishment.setStatus(Status.CREATED);

        response = new ReplenishmentResponse(1L, 1L, 2L, 10, Status.CREATED, 3L, null, null, LocalDateTime.now());
    }

    @Test
    void createReplenishment_validRequest_returnsResponse() {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, 10, 3L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(destinationLocation));
        when(stockRepository.findByLocationId(3L)).thenReturn(Optional.empty());
        when(replenishmentRepository.save(any(Replenishment.class))).thenReturn(replenishment);
        when(replenishmentMapper.toResponse(replenishment)).thenReturn(response);

        ReplenishmentResponse result = replenishmentService.createReplenishment(request);

        assertThat(result).isEqualTo(response);
        verify(replenishmentRepository).save(any(Replenishment.class));
    }

    @Test
    void updateReplenishment_validRequest_returnsUpdatedResponse() {
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(1L, 1L, 20, Status.COMPLETED, 3L);

        when(replenishmentRepository.findById(eq(1L))).thenReturn(Optional.of(replenishment));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(destinationLocation));
        when(stockRepository.findByLocationId(3L)).thenReturn(Optional.empty());
        when(replenishmentRepository.save(replenishment)).thenReturn(replenishment);
        when(replenishmentMapper.toResponse(replenishment)).thenReturn(response);

        ReplenishmentResponse result = replenishmentService.updateReplenishment(1L, request);

        assertThat(result).isEqualTo(response);
        assertThat(replenishment.getRequestedQuantity()).isEqualTo(20);
        assertThat(replenishment.getStatus()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void cancelReplenishment_releasesTransportUnit() {
        when(replenishmentRepository.findById(1L)).thenReturn(Optional.of(replenishment));
        TransportUnit tu = new TransportUnit("TU123456");
        tu.setReplenishment(replenishment);

        when(transportUnitRepository.findAllByReplenishment(replenishment)).thenReturn(List.of(tu));

        when(allocationRepository.findAllByTaskId(1L)).thenReturn(List.of());
        when(replenishmentRepository.save(any())).thenReturn(replenishment);

        replenishmentService.cancelReplenishment(1L);

        assertThat(tu.getReplenishment()).isNull();
        verify(transportUnitRepository).save(tu);
        assertThat(replenishment.getStatus()).isEqualTo(Status.CANCELED);
    }

    @Test
    void assignReplenishment_validRequest_createsTaskAndUpdatesStatus() {
        replenishment.setStatus(Status.CREATED);
        when(replenishmentRepository.findById(1L)).thenReturn(Optional.of(replenishment));

        Task newTask = new Task();
        ReflectionTestUtils.setField(newTask, "id", 500L);
        when(taskService.createTask(TaskType.REPLENISHMENT, 10, 1L)).thenReturn(newTask);

        replenishmentService.assignReplenishment(1L, 2L);

        verify(taskService).createTask(TaskType.REPLENISHMENT, 10, 1L);
        verify(replenishmentRepository).saveAndFlush(replenishment);
        assertThat(replenishment.getTask()).isPresent();
    }
}
