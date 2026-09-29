//
//  PhoneFormat.swift
//  One — Ders Defteri
//
//  Türkiye telefon numarası girişlerini okunur formata çevirir.
//

import Foundation

enum TurkishPhoneFormat {
    static func format(_ value: String) -> String {
        var digits = value.filter(\.isNumber)

        if digits.hasPrefix("90"), digits.count > 11 {
            digits = String(digits.dropFirst(2))
        }

        if !digits.hasPrefix("0"), !digits.isEmpty {
            digits = "0" + digits
        }

        digits = String(digits.prefix(11))

        let groups = [
            prefix(digits, 4),
            slice(digits, from: 4, length: 3),
            slice(digits, from: 7, length: 2),
            slice(digits, from: 9, length: 2)
        ].filter { !$0.isEmpty }

        return groups.joined(separator: " ")
    }

    private static func prefix(_ value: String, _ count: Int) -> String {
        String(value.prefix(count))
    }

    private static func slice(_ value: String, from start: Int, length: Int) -> String {
        guard value.count > start else { return "" }
        let startIndex = value.index(value.startIndex, offsetBy: start)
        let endIndex = value.index(startIndex, offsetBy: min(length, value.distance(from: startIndex, to: value.endIndex)))
        return String(value[startIndex..<endIndex])
    }
}
