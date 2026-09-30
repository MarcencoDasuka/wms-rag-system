<template>
  <div class="p-6">
    <ConfirmDialog />
    <AppDataTable
      v-model:selection="selectedUsers"
      :value="users"
      :loading="loading"
      :filterFields="userFilterFields"
      stripedRows
      class="p-datatable-sm"
      emptyMessage="No users found."
    >
      <template #toolbar>
        <Button
          icon="pi pi-refresh"
          size="small"
          severity="secondary"
          outlined
          :loading="loading"
          aria-label="Refresh"
          @click="loadUsers"
        />
        <Button
          label="Create"
          icon="pi pi-user-plus"
          severity="success"
          @click="openCreateDialog"
        />
        <Button
          :label="editMode ? 'Exit Edit' : 'Edit'"
          icon="pi pi-pencil"
          severity="warning"
          outlined
          @click="toggleEditMode"
        />
        <Button
          v-if="editMode && isDev"
          label="Edit Selected"
          icon="pi pi-pencil"
          severity="warning"
          outlined
          :disabled="selectedUsers.length !== 1"
          @click="openEditDialog(selectedUsers[0])"
        />
        <Button
          v-if="editMode"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          outlined
          :disabled="!deletableSelectedUsers.length"
          @click="deleteSelectedUsers"
        />
        <span v-if="editMode" class="app-muted text-sm">{{ selectedUsers.length }} selected</span>
      </template>

      <Column v-if="editMode" selectionMode="multiple" headerStyle="width: 3rem" />
      <Column field="username" header="Username" sortable filter>
        <template #body="{ data }">
          <span class="app-title font-semibold">{{ data.username }}</span>
        </template>
      </Column>
      <Column field="email" header="Email" filter></Column>
      <Column field="formattedRole" header="Role" sortable filter>
        <template #body="{ data }">
          <Tag :severity="getRoleSeverity(data.userRole)" :value="data.formattedRole" />
        </template>
      </Column>
    </AppDataTable>

    <Dialog
      v-model:visible="dialogVisible"
      :header="dialogMode === 'add' ? 'Register New User' : 'Edit User'"
      :modal="true"
      class="w-full max-w-md"
    >
      <div class="flex flex-col gap-4 mt-2">
        <div class="flex flex-col gap-2">
          <label for="username" class="app-subtitle font-medium"
          >Username <span class="text-red-500">*</span></label
          >
          <InputText
            id="username"
            v-model="formData.username"
            :class="{ 'p-invalid': formData.username && formData.username.includes('@') }"
            required
            autofocus
            class="w-full"
          />
          <small
            v-if="formData.username && formData.username.includes('@')"
            class="text-red-500 text-xs font-medium"
          >
            Username cannot contain the '@' character. Please use simple characters, numbers or underscores.
          </small>
        </div>

        <template v-if="dialogMode === 'add'">
          <div class="flex flex-col gap-2">
            <label for="email" class="app-subtitle font-medium"
            >Email <span class="text-red-500">*</span></label
            >
            <InputText id="email" type="email" v-model="formData.email" required class="w-full" />
          </div>
        </template>

        <div class="flex flex-col gap-2">
          <label for="role" class="app-subtitle font-medium"
          >Role <span class="text-red-500">*</span></label
          >
          <Dropdown
            id="role"
            v-model="formData.userRole"
            :options="roles"
            optionLabel="label"
            optionValue="value"
            placeholder="Select a Role"
            filter
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          text
          severity="secondary"
          @click="dialogVisible = false"
        />
        <Button
          :label="dialogMode === 'add' ? 'Register' : 'Save Changes'"
          icon="pi pi-check"
          :severity="dialogMode === 'add' ? 'success' : 'warning'"
          :loading="actionLoading"
          @click="submitAction"
          :disabled="!isFormValid"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'

import { userApi } from '@/api/userApi'
import { useAuthStore } from '@/stores/auth'

const toast = useToast()
const confirm = useConfirm()
const authStore = useAuthStore()

const users = ref([])
const selectedUsers = ref([])
const loading = ref(false)
const actionLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('add')
const editMode = ref(false)

const isDev = computed(() => authStore.role === 'ROLE_DEV')
const deletableSelectedUsers = computed(() => selectedUsers.value.filter(canDelete))

const userFilterFields = [
  { field: 'username', label: 'Username' },
  { field: 'email', label: 'Email' },
  { field: 'formattedRole', label: 'Role' },
]

const formData = ref({
  id: null,
  username: '',
  email: '',
  userRole: null,
  originalRole: null,
})

const roles = computed(() => {
  const allRoles = [
    { value: 'ROLE_SUPERVISOR', label: 'Supervisor' },
    { value: 'ROLE_OPERATOR', label: 'Operator' },
    { value: 'ROLE_DEV', label: 'Developer' },
  ]

  if (dialogMode.value === 'edit' && formData.value.originalRole === 'ROLE_DEV') {
    return allRoles
  }
  return allRoles.filter((role) => role.value !== 'ROLE_DEV')
})

const isUsernameValid = computed(() => {
  return formData.value.username && !formData.value.username.includes('@')
})

const isFormValid = computed(() => {
  if (dialogMode.value === 'add') {
    return (
      isUsernameValid.value &&
      formData.value.username.trim() &&
      formData.value.email.trim() &&
      formData.value.userRole
    )
  } else {
    return isUsernameValid.value && formData.value.username.trim() && formData.value.userRole
  }
})

const getRoleSeverity = (role) => {
  if (role === 'ROLE_SUPERVISOR') return 'warning'
  if (role === 'ROLE_DEV') return 'danger'
  return 'success'
}

const formatRoleLabel = (roleValue) => {
  if (roleValue === 'ROLE_SUPERVISOR') return 'Supervisor'
  if (roleValue === 'ROLE_DEV') return 'Developer'
  if (roleValue === 'ROLE_OPERATOR') return 'Operator'
  return roleValue
}

const canDelete = (user) => {
  if (user.username === authStore.username) return false
  if (authStore.role === 'ROLE_DEV') return true
  if (authStore.role === 'ROLE_SUPERVISOR' && user.userRole === 'ROLE_OPERATOR') return true
  return false
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await userApi.getAll()
    users.value = res.data.map(u => ({
      ...u,
      formattedRole: formatRoleLabel(u.userRole)
    }))
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Load Failed', detail: error.message, life: 4000 })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'add'
  formData.value = { id: null, username: '', email: '', userRole: null, originalRole: null }
  dialogVisible.value = true
}

const toggleEditMode = () => {
  editMode.value = !editMode.value
  selectedUsers.value = []
}

const openEditDialog = (user) => {
  dialogMode.value = 'edit'
  formData.value = {
    id: user.id,
    username: user.username,
    email: '',
    userRole: user.userRole,
    originalRole: user.userRole,
  }
  dialogVisible.value = true
}

const submitAction = async () => {
  if (dialogMode.value === 'add') await registerUser()
  else await updateUser()
}

const handleBackendError = (error) => {
  if (error.response?.status === 400 && typeof error.response.data === 'object' && !error.response.data.error) {
    for (const [field, msg] of Object.entries(error.response.data)) {
      toast.add({ severity: 'error', summary: `Invalid ${field}`, detail: msg, life: 6000 })
    }
  } else {
    toast.add({ severity: 'error', summary: 'Error', detail: error.response?.data?.error || 'Operation failed', life: 5000 })
  }
}

const registerUser = async () => {
  actionLoading.value = true
  try {
    const payload = { username: formData.value.username, email: formData.value.email, userRole: formData.value.userRole }
    await userApi.register(payload)
    toast.add({ severity: 'success', summary: 'Success', detail: 'User registered successfully', life: 3000 })
    dialogVisible.value = false
    await loadUsers()
  } catch (error) {
    handleBackendError(error)
  } finally {
    actionLoading.value = false
  }
}

const updateUser = async () => {
  actionLoading.value = true
  try {
    const payload = { username: formData.value.username, userRole: formData.value.userRole }
    await userApi.update(formData.value.id, payload)
    toast.add({ severity: 'success', summary: 'Success', detail: 'User updated successfully', life: 3000 })
    dialogVisible.value = false
    await loadUsers()
  } catch (error) {
    handleBackendError(error)
  } finally {
    actionLoading.value = false
  }
}

const deleteSelectedUsers = () => {
  confirm.require({
    message: `Delete ${deletableSelectedUsers.value.length} selected user(s)?`,
    header: 'Delete Selected Users',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      loading.value = true
      try {
        await Promise.all(deletableSelectedUsers.value.map((user) => userApi.delete(user.id)))
        toast.add({ severity: 'success', summary: 'Deleted', detail: `${deletableSelectedUsers.value.length} user(s) deleted.`, life: 3000 })
        selectedUsers.value = []
        await loadUsers()
      } catch (error) {
        handleBackendError(error)
      } finally {
        loading.value = false
      }
    },
  })
}

onMounted(loadUsers)
</script>
