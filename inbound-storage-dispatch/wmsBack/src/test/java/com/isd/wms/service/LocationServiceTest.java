package com.isd.wms.service;

import com.isd.wms.dto.location.LocationCreateRequest;
import com.isd.wms.dto.location.LocationResponse;
import com.isd.wms.dto.location.LocationUpdateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.DuplicateBarcodeException;
import com.isd.wms.exception.LocationNotFoundException;
import com.isd.wms.mapper.LocationMapper;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock private LocationRepository locationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private ReplenishmentRepository replenishmentRepository;
    @Mock private OrderRepository orderRepository;

    @Spy private LocationMapper locationMapper = new LocationMapper();

    @InjectMocks
    private LocationService locationService;

    @Test
    void deletesLocationSuccessfully() {
        Location existingLocation = location(1L, "A-01", Zone.PICKING, true);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(existingLocation));

        locationService.deleteLocation(1L);

        verify(locationRepository).save(existingLocation);
        assertThat(existingLocation.getIsActive()).isFalse();
        assertThat(existingLocation.getAvailable()).isFalse();
    }

    @Test
    void rejectsDeletingLocationWhenOccupiedByStock() {
        when(stockRepository.existsByLocationIdAndAvailableIsTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> locationService.deleteLocation(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("You cannot delete a location while there is a product in it");

        verify(locationRepository, never()).save(any());
    }

    @Test
    void rejectsDeletingLocationWithActiveReplenishments() {
        when(stockRepository.existsByLocationIdAndAvailableIsTrue(1L)).thenReturn(false);
        when(replenishmentRepository.existsByDestinationLocationIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> locationService.deleteLocation(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active replenishment");

        verify(locationRepository, never()).save(any());
    }

    @Test
    void rejectsDeletingLocationWithActiveOrders() {
        when(stockRepository.existsByLocationIdAndAvailableIsTrue(1L)).thenReturn(false);
        when(replenishmentRepository.existsByDestinationLocationIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(orderRepository.existsByDestinationLocationIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> locationService.deleteLocation(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active orders");

        verify(locationRepository, never()).save(any());
    }

    @Test
    void rejectsActivatingInactiveLocation() {
        Location inactiveLocation = location(1L, "A-01", Zone.PICKING, false);
        inactiveLocation.setIsActive(false);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(inactiveLocation));

        assertThatThrownBy(() -> locationService.updateLocation(
            1L,
            new LocationUpdateRequest("A-01", "A-01", Zone.PICKING, "Desc", true)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot set an inactive/deleted location as available");
    }

    @Test
    void getsAllLocations() {
        List<Location> mockList = List.of(
            location(1L, "A-01", Zone.PICKING, true),
            location(2L, "B-02", Zone.REPLENISHMENT, false)
        );
        lenient().when(locationRepository.findAllByIsActiveTrue()).thenReturn(mockList);
        lenient().when(locationRepository.findAll()).thenReturn(mockList);

        List<LocationResponse> responses = locationService.getAllLocations();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(LocationResponse::barcode).contains("A-01", "B-02");
    }

    @Test
    void createsLocationSuccessfully() {
        when(locationRepository.existsByBarcodeIgnoreCase("A-01")).thenReturn(false);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationResponse response = locationService.createLocation(
            new LocationCreateRequest("Name A", "A-01", Zone.PICKING, "Main storage")
        );

        assertThat(response.barcode()).isEqualTo("A-01");
        assertThat(response.zone()).isEqualTo(Zone.PICKING);
    }

    @Test
    void rejectsCreationWhenBarcodeExists() {
        when(locationRepository.existsByBarcodeIgnoreCase("A-01")).thenReturn(true);

        assertThatThrownBy(() -> locationService.createLocation(new LocationCreateRequest("Name A", "A-01", Zone.PICKING, null)))
            .isInstanceOf(DuplicateBarcodeException.class)
            .hasMessageContaining("A-01");
    }

    @Test
    void getsLocationById() {
        when(locationRepository.findById(1L))
            .thenReturn(Optional.of(location(1L, "A-01", Zone.PICKING, true)));

        LocationResponse response = locationService.getLocationById(1L);

        assertThat(response.barcode()).isEqualTo("A-01");
    }

    @Test
    void throwsExceptionWhenLocationNotFound() {
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getLocationById(99L))
            .isInstanceOf(LocationNotFoundException.class);
    }

    @Test
    void updatesLocationSuccessfullyWithoutChangingCode() {
        Location existingLocation = location(1L, "A-01", Zone.PICKING, true);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(existingLocation));
        when(locationRepository.save(existingLocation)).thenReturn(existingLocation);

        LocationResponse response = locationService.updateLocation(
            1L,
            new LocationUpdateRequest("Updated Name", "A-01", Zone.PICKING, "Updated desc", false)
        );

        assertThat(response.zone()).isEqualTo(Zone.PICKING);
        assertThat(response.available()).isFalse();
    }

    @Test
    void rejectsUpdateWhenNewBarcodeExists() {
        Location existingLocation = location(1L, "A-01", Zone.PICKING, true);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(existingLocation));
        when(locationRepository.existsByBarcodeIgnoreCase("B-02")).thenReturn(true);

        assertThatThrownBy(() -> locationService.updateLocation(1L, new LocationUpdateRequest("Name B", "B-02", Zone.REPLENISHMENT, null, true)))
            .isInstanceOf(DuplicateBarcodeException.class);
    }

    private Location location(Long id, String code, Zone zone, boolean available) {
        Location location = new Location("Name " + code, code, zone, "Desc");
        location.setAvailable(available);
        location.setIsActive(true);
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }
}
