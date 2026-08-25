from __future__ import annotations

from collections.abc import Iterable

from .base import Operator


class OperatorRegistry:
    def __init__(self, operators: Iterable[Operator] = ()) -> None:
        self._operators = {(operator.name, operator.version): operator for operator in operators}

    def register(self, operator: Operator) -> None:
        key = (operator.name, operator.version)
        if key in self._operators:
            raise ValueError(f"duplicate operator: {operator.name}/{operator.version}")
        self._operators[key] = operator

    def resolve(self, name: str, version: str) -> Operator:
        try:
            return self._operators[(name, version)]
        except KeyError as error:
            raise KeyError(f"unknown operator version: {name}/{version}") from error
