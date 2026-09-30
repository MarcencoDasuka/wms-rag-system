import apiClient from './index'

export const productApi = {
  getAllProducts() {
    return apiClient.get('/products')
  },
  getProductById(productId) {
    return apiClient.get(`/products/${productId}`)
  },
  createProduct(payload) {
    return apiClient.post('/products', payload)
  },
  updateProduct(productId, payload) {
    return apiClient.put(`/products/${productId}`, payload)
  },
  deleteProduct(productId) {
    return apiClient.delete(`/products/${productId}`)
  },
  searchProducts(params) {
    return apiClient.get('/products/search', { params })
  },
  getAllProductsWithQuantityInZone(zone) {
    return apiClient.get('/products/quantities', {
      params: { zone },
    })
  },
  importProducts(payload) {
    return apiClient.post('/products/imports', payload, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  getCategories() {
    return apiClient.get('/categories')
  },
  createCategory(payload) {
    return apiClient.post('/categories', payload)
  },
  importCategories(payload) {
    return apiClient.post('/categories/imports', payload, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
