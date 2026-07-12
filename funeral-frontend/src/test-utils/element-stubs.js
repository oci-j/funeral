const elTableKey = Symbol('el-table')

const baseStub = (template, options = {}) => ({
  template,
  inheritAttrs: true,
  ...options,
})

export const elementStubs = {
  'el-alert': baseStub(
    '<div class="el-alert" :class="type ? \'el-alert--\' + type : \'\'"><div v-if="title" class="el-alert__title">{{ title }}</div><div v-if="description" class="el-alert__description">{{ description }}</div><slot /></div>',
    { props: ['title', 'type', 'description'] }
  ),
  'el-aside': baseStub('<aside class="el-aside"><slot /></aside>'),
  'el-button': {
    emits: ['click'],
    inheritAttrs: true,
    template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-button-group': baseStub('<div class="el-button-group"><slot /></div>'),
  'el-card': baseStub(
    '<div class="el-card"><div class="el-card__header"><slot name="header" /></div><div class="el-card__body"><slot /></div></div>'
  ),
  'el-checkbox': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    inheritAttrs: true,
    template:
      '<label class="el-checkbox"><input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)"><slot /></label>',
  },
  'el-collapse': baseStub('<div class="el-collapse"><slot /></div>'),
  'el-collapse-item': baseStub(
    '<div class="el-collapse-item"><div class="el-collapse-item__header">{{ title }}</div><div class="el-collapse-item__content"><slot /></div></div>',
    { props: ['title', 'name'] }
  ),
  'el-col': baseStub('<div class="el-col"><slot /></div>'),
  'el-container': baseStub('<div class="el-container"><slot /></div>'),
  'el-descriptions': baseStub('<div class="el-descriptions"><slot /></div>'),
  'el-descriptions-item': baseStub(
    '<div class="el-descriptions-item"><span class="el-descriptions-item__label">{{ label }}</span><div class="el-descriptions-item__content"><slot /></div></div>',
    { props: ['label'] }
  ),
  'el-dialog': {
    props: ['modelValue', 'title'],
    emits: ['update:modelValue', 'closed'],
    inheritAttrs: true,
    template:
      '<div v-if="modelValue" class="el-dialog"><div class="el-dialog__header"><div v-if="title" class="el-dialog__title">{{ title }}</div><button class="el-dialog__close" @click="$emit(\'update:modelValue\', false); $emit(\'closed\')">x</button></div><div class="el-dialog__body"><slot /></div><div class="el-dialog__footer"><slot name="footer" /></div></div>',
  },
  'el-divider': baseStub('<div class="el-divider"><slot /></div>'),
  'el-dropdown': {
    name: 'ElDropdown',
    props: ['disabled'],
    emits: ['command'],
    inheritAttrs: true,
    template:
      '<div class="el-dropdown"><div class="el-dropdown-selfdefine"><slot /></div><div class="el-dropdown__popper" style="display:none"><slot name="dropdown" /></div></div>',
  },
  'el-dropdown-item': {
    name: 'ElDropdownItem',
    props: ['command'],
    emits: ['command'],
    inheritAttrs: true,
    template: '<div class="el-dropdown-item" @click="$emit(\'command\', command)"><slot /></div>',
  },
  'el-dropdown-menu': baseStub('<div class="el-dropdown-menu"><slot /></div>'),
  'el-empty': baseStub('<div class="el-empty"><slot /></div>', { props: ['description'] }),
  'el-form': baseStub('<form class="el-form"><slot /></form>', {
    methods: {
      validate() {
        return Promise.resolve(true)
      },
    },
  }),
  'el-form-item': baseStub('<div class="el-form-item"><slot /></div>'),
  'el-header': baseStub('<header class="el-header"><slot /></header>'),
  'el-icon': baseStub('<span class="el-icon"><slot /></span>'),
  'el-input': {
    props: ['modelValue', 'type', 'readonly', 'placeholder'],
    emits: ['update:modelValue', 'change', 'keyup'],
    inheritAttrs: true,
    template:
      '<div class="el-input-wrapper"><input class="el-input" :value="modelValue" :type="type" :readonly="readonly" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)" @change="$emit(\'change\', $event.target.value)" @keyup="$emit(\'keyup\', $event)" /><div class="el-input__append"><slot name="append" /></div></div>',
  },
  'el-link': {
    emits: ['click'],
    inheritAttrs: true,
    template: '<a class="el-link" @click.prevent="$emit(\'click\')"><slot /></a>',
  },
  'el-loading': baseStub('<div class="el-loading"><slot /></div>'),
  'el-main': baseStub('<main class="el-main"><slot /></main>'),
  'el-menu': {
    props: ['defaultActive'],
    emits: ['select'],
    inheritAttrs: true,
    template: '<div class="el-menu" :data-default-active="defaultActive"><slot /></div>',
  },
  'el-menu-item': {
    props: ['index'],
    inheritAttrs: true,
    template:
      '<div class="el-menu-item" :data-index="index" @click="$emit(\'select\', index)"><slot /></div>',
  },
  'el-option': {
    props: ['label', 'value'],
    inheritAttrs: true,
    template: '<option class="el-option" :value="value">{{ label }}</option>',
  },
  'el-result': {
    props: ['icon', 'title'],
    inheritAttrs: true,
    template:
      '<div class="el-result"><div class="el-result__icon">{{ icon }}</div><div class="el-result__title">{{ title }}</div><div class="el-result__subtitle"><slot name="subTitle" /></div></div>',
  },
  'el-row': baseStub('<div class="el-row"><slot /></div>'),
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    inheritAttrs: true,
    template:
      '<select class="el-select" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
  },
  'el-switch': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    inheritAttrs: true,
    template:
      '<input type="checkbox" class="el-switch" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)">',
  },
  'el-tab-pane': {
    props: ['label'],
    inheritAttrs: true,
    template: '<div class="el-tab-pane"><slot /></div>',
  },
  'el-table': {
    name: 'ElTable',
    props: ['data'],
    provide() {
      return { [elTableKey]: this }
    },
    template: '<table class="el-table"><slot /></table>',
  },
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['prop', 'label'],
    inject: { elTable: { from: elTableKey, default: () => null } },
    template:
      '<td class="el-table-column"><template v-if="elTable && elTable.data"><div v-for="(row, idx) in elTable.data" :key="idx" class="el-table-cell"><slot :row="row" :$index="idx" /></div></template><slot v-else :row="null" :$index="0" /></td>',
  },
  'el-tabs': baseStub('<div class="el-tabs"><slot /></div>'),
  'el-tag': baseStub('<span class="el-tag"><slot /></span>'),
  'el-text': {
    props: ['type', 'size'],
    inheritAttrs: true,
    template: '<span class="el-text" :class="type ? \'el-text--\' + type : \'\'"><slot /></span>',
  },
  'el-tooltip': baseStub('<div class="el-tooltip"><slot /></div>'),
  'el-upload': {
    name: 'ElUpload',
    props: ['fileList', 'autoUpload', 'accept'],
    emits: ['update:file-list'],
    inheritAttrs: true,
    template:
      '<div class="el-upload"><slot /><div class="el-upload__tip"><slot name="tip" /></div></div>',
  },
  'vue-json-pretty': baseStub('<div class="vue-json-pretty"><slot /></div>', { props: ['data'] }),
}

const iconStub = { template: '<span class="el-icon-svg" />' }

export const iconStubs = Object.fromEntries(
  [
    'Download',
    'DocumentCopy',
    'QuestionFilled',
    'Back',
    'Document',
    'Delete',
    'Plus',
    'Edit',
    'Key',
    'UploadFilled',
    'Box',
    'Loading',
    'Expand',
    'Fold',
    'View',
    'Folder',
    'Document',
    'Files',
    'User',
    'SwitchButton',
    'Tools',
    'Lock',
    'Unlock',
    'ArrowDown',
    'InfoFilled',
    'Menu',
    'HomeFilled',
  ].map(name => [name, iconStub])
)

export const loadingDirective = {
  mounted() {},
  updated() {},
  unmounted() {},
}
