import Foundation

// Compile this into a dylib via
// swiftc thermal_state.swift -emit-library
@_cdecl("thermal_state")
public func thermal_state() -> Int {
    return ProcessInfo().thermalState.rawValue
}
