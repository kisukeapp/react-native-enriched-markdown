/**
 * Bridges EnrichedMarkdownTextInput into React Native's TextInputState focus
 * registry so it participates in RN's keyboard-dismiss behavior (issue #577).
 *
 * Registering a non-TextInput host component is safe: TextInputState's
 * blurTextInput dispatches the built-in TextInput's "blur" command at the
 * registered instance, and Fabric command dispatch is name-based — it works
 * as long as the component ships a command with that name, which ours does.
 *
 * A deep import is required because the public TextInput.State API lacks the
 * registerInput/focusInput/blurInput bookkeeping functions.
 */
import type { HostInstance } from 'react-native';
interface TextInputStateModule {
    registerInput(input: HostInstance): void;
    unregisterInput(input: HostInstance): void;
    focusInput(input: HostInstance | null): void;
    blurInput(input: HostInstance | null): void;
    blurTextInput(input: HostInstance | null): void;
    currentlyFocusedInput(): HostInstance | null;
}
export declare const TextInputState: TextInputStateModule;
export {};
//# sourceMappingURL=textInputState.d.ts.map