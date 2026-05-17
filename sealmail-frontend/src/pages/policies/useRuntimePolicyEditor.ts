import { Form } from 'antd';
import type {
  GmEdgePolicyFormValues,
  QuarantinePolicyFormValues,
  RelayPolicyFormValues,
  SmimeSuitePolicyFormValues,
  TestMailValues,
} from '../settings/settingsUtils';
import { useSettings } from '../settings/useSettings';

export const useRuntimePolicyEditor = () => {
  const [relayForm] = Form.useForm<RelayPolicyFormValues>();
  const [quarantineForm] = Form.useForm<QuarantinePolicyFormValues>();
  const [gmEdgeForm] = Form.useForm<GmEdgePolicyFormValues>();
  const [smimeSuiteForm] = Form.useForm<SmimeSuitePolicyFormValues>();
  const [testForm] = Form.useForm<TestMailValues>();

  const settingsState = useSettings({
    gmEdgeForm,
    quarantineForm,
    relayForm,
    smimeSuiteForm,
    testForm,
  });

  return {
    gmEdgeForm,
    quarantineForm,
    relayForm,
    smimeSuiteForm,
    ...settingsState,
  };
};
