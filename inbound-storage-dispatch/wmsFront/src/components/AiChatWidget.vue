<template>
  <div class="fixed bottom-6 right-6 z-50">
    <transition name="fade-slide">
      <div v-if="isOpen" class="absolute bottom-0 right-0 origin-bottom-right app-card shadow-2xl rounded-2xl w-[90vw] md:w-[600px] h-[650px] flex flex-col border border-white/10 overflow-hidden bg-gray-900">
        <div class="p-4 border-b border-gray-700 flex justify-between items-center bg-gray-800">
          <h2 class="text-lg font-bold text-white flex items-center gap-2 m-0">
            <i class="pi pi-sparkles"></i> WMS Assistant
          </h2>
          <Button icon="pi pi-times" text rounded severity="secondary" @click="isOpen = false" />
        </div>

        <div class="flex-1 overflow-y-auto p-4 flex flex-col gap-4 scroll-smooth" ref="chatBox" @click="handleChatInteraction">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            :class="['p-3 rounded-xl max-w-[90%] text-sm', msg.role === 'user' ? 'bg-blue-600 text-white self-end' : 'bg-gray-800 text-gray-200 self-start border border-gray-700 shadow-md']"
          >
            <div v-if="msg.role === 'ai'" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else>{{ msg.content }}</div>
          </div>
          <div v-if="loading" class="text-gray-400 self-start p-3 text-sm italic flex items-center gap-2">
            <i class="pi pi-spin pi-spinner"></i> Analyzing warehouse data...
          </div>
        </div>

        <div class="p-3 border-t border-gray-700 flex gap-2 bg-gray-800">
          <InputText v-model="inputMsg" class="flex-1" @keyup.enter="sendMessage" placeholder="Ask about stock, tasks..." />
          <Button icon="pi pi-send" severity="primary" @click="sendMessage" :loading="loading" />
        </div>
      </div>
    </transition>

    <transition name="fade-btn">
      <Button v-if="!isOpen" icon="pi pi-sparkles" class="absolute bottom-0 right-0 w-14 h-14 rounded-full shadow-xl hover:scale-105 transition-all duration-300" size="large" severity="primary" @click="isOpen = true" />
    </transition>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { chatApi } from '@/api/chatApi';
import { useToast } from 'primevue/usetoast';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const router = useRouter();
const toast = useToast();
const isOpen = ref(false);
const messages = ref([{
  role: 'ai',
  content: 'Hello! I am your WMS AI Assistant. How can I help you today?'
}]);
const inputMsg = ref('');
const loading = ref(false);
const chatBox = ref(null);

marked.setOptions({ breaks: true, gfm: true });

const renderMarkdown = (text) => {
  if (!text) return '';
  return DOMPurify.sanitize(marked.parse(text), { ADD_ATTR: ['target'] });
};

const scrollToBottom = async () => {
  await nextTick();
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight;
  }
};

const handleChatInteraction = async (event) => {
  const linkTarget = event.target.closest('a');
  if (linkTarget) {
    let href = linkTarget.getAttribute('href');
    if (href) {
      event.preventDefault();
      event.stopPropagation();

      if (href.startsWith('https://supervisor')) {
        href = href.replace('https://supervisor', '/supervisor');
      } else if (href.startsWith('http://supervisor')) {
        href = href.replace('http://supervisor', '/supervisor');
      }

      router.push(href);
      return;
    }
  }

  if (event.target && event.target.tagName === 'CODE') {
    event.preventDefault();
    event.stopPropagation();

    const textToCopy = event.target.innerText;
    try {
      await navigator.clipboard.writeText(textToCopy);
      toast.add({
        severity: 'success',
        summary: 'Copied!',
        detail: `${textToCopy} copied to clipboard`,
        life: 2000
      });
    } catch (err) {
      console.error('Failed to copy text: ', err);
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to copy to clipboard',
        life: 2000
      });
    }
  }
};

const sendMessage = async () => {
  if (!inputMsg.value.trim() || loading.value) return;

  const userText = inputMsg.value;
  messages.value.push({ role: 'user', content: userText });
  inputMsg.value = '';
  loading.value = true;
  await scrollToBottom();

  try {
    const res = await chatApi.sendMessage(userText);
    messages.value.push({ role: 'ai', content: res.data.reply });
  } catch (e) {
    messages.value.push({ role: 'ai', content: '**Error:** Connection to AI core failed.' });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
};
</script>

<style scoped>
:deep(.markdown-body a) {
  color: #3b82f6;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  font-weight: 600;
  transition: color 0.2s;
}

:deep(.markdown-body a:hover) {
  color: #60a5fa;
}

:deep(.markdown-body p) {
  margin-bottom: 0.5rem;
}

:deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

:deep(.markdown-body strong) {
  color: #93dfff;
}

:deep(.markdown-body ul) {
  list-style-type: disc;
  padding-left: 1.5rem;
  margin-bottom: 0.5rem;
}

:deep(.markdown-body code) {
  cursor: pointer;
  background-color: rgba(59, 130, 246, 0.15);
  color: #93c5fd;
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  transition: all 0.2s ease;
  font-family: monospace;
}

:deep(.markdown-body code:hover) {
  background-color: rgba(59, 130, 246, 0.35);
  color: #bfdbfe;
}

:deep(.markdown-body code:active) {
  transform: scale(0.95);
}

:deep(.markdown-body table) {
  width: 100%;
  border-collapse: collapse;
  margin-top: 0.5rem;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
  display: block;
  overflow-x: auto;
  white-space: nowrap;
}

:deep(.markdown-body th) {
  background-color: rgba(255, 255, 255, 0.05);
  border: 1px solid #4b5563;
  padding: 6px 8px;
  text-align: left;
  font-weight: bold;
}

:deep(.markdown-body td) {
  border: 1px solid #4b5563;
  padding: 6px 8px;
}

.fade-slide-enter-active, .fade-slide-leave-active {
  transition: opacity 0.4s cubic-bezier(0.22, 1, 0.36, 1), transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform, opacity;
}

.fade-slide-enter-from, .fade-slide-leave-to {
  opacity: 0;
  transform: scale(0.85);
}

.fade-slide-enter-to, .fade-slide-leave-from {
  opacity: 1;
  transform: scale(1);
}

.fade-btn-enter-active, .fade-btn-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
  transition-delay: 0.1s;
}

.fade-btn-enter-from, .fade-btn-leave-to {
  opacity: 0;
  transform: scale(0.5);
}
</style>
