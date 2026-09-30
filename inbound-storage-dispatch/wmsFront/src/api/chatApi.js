import apiClient from './index';

export const chatApi = {
  sendMessage: (message) => apiClient.post('/v1/chat', { message })
};
