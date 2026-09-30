import apiClient from './index'

export const allocationApi = {
  getCurrentTaskSummary() {
    return apiClient.get('/v1/allocations/operator/current/summary')
  },
  getSupervisorAllocations() {
    return apiClient.get('/v1/allocations')
  },
  startCurrentTask() {
    return apiClient.post('/v1/allocations/operator/current/start')
  },
  completeCurrentOrder() {
    return apiClient.post('/v1/allocations/operator/current/complete')
  },
  completeAssignedAllocation(allocationId) {
    return apiClient.post(`/v1/allocations/${allocationId}/complete`)
  },
  scanSourceLocation(allocationId, barcode) {
    return apiClient.post(`/v1/allocations/${allocationId}/location`, { barcode })
  },
  scanProduct(allocationId, barcode) {
    return apiClient.post(`/v1/allocations/${allocationId}/product`, { barcode })
  },
  confirmPickedQuantity(allocationId, payload) {
    return apiClient.post(`/v1/allocations/${allocationId}/confirm-quantity`, payload)
  },
  scanTransportUnit(id, barcode, isOrder) {
    return apiClient.post(`/v1/allocations/${id}/scan-tu`, {
      barcode: barcode,
      isOrder: isOrder,
    })
  },
  dispatchAllocation(id, currentBarcode) {
    return apiClient.post(`/v1/allocations/${id}/dispatch?currentBarcode=${currentBarcode}`)
  }
}
